package dev.lumina.client.online;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.cosmetics.CapeLibrary;
import dev.lumina.client.feature.cosmetics.CosmeticsModule;
import dev.lumina.client.feature.cosmetics.ParticleEffects;
import dev.lumina.client.mixin.PlayerTabOverlayAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.jspecify.annotations.Nullable;

/**
 * Lumina's own server: it tells other Lumina players that you use Lumina and what you wear, and it
 * says which players around you use Lumina. Lumina Settings > Lumina Online turns it off; then
 * nothing at all is sent (the installer asks once too).
 *
 * <p>Sign-in uses the player key Mojang gives every account (signed chat): the game signs Lumina's
 * challenge with it, and the server checks that signature and Mojang's signature on the key. No
 * token and no private key leave the PC. Lumina's server answers with its own token, kept in memory.
 *
 * <p>Big servers: only players in view (closest first) and the tab list as shown are looked up,
 * only UUIDs not known yet, 100 per request, at most 4 requests per minute, and every answer is
 * kept for 10 minutes.
 */
public final class LuminaOnline {
	private static final String API = apiUrl();
	/** Dev clients only talk to a server named with -Dlumina.api, never to the real one by accident. */
	private static final boolean ENABLED = !Lumina.DEV || System.getProperty("lumina.api") != null;
	/** A local wrangler dev offers a sign-in without Mojang for offline test accounts. */
	private static final boolean DEV_LOGIN = Lumina.DEV && (API.startsWith("http://127.0.0.1") || API.startsWith("http://localhost"));

	private static final long CACHE_MS = 10 * 60_000L;
	private static final int BATCH = 100;
	private static final int TAB_LIST = 80; // what the tab list shows at most
	private static final double BURST = 4;
	private static final long REFILL_MS = 15_000L;
	private static final long HEARTBEAT_MS = 10 * 60_000L;
	private static final long SETTLE_MS = 2_000L;
	private static final int MAX_CACHE = 20_000;

	/** Another Lumina player's cosmetics; null fields mean they wear nothing there. */
	public record Remote(@Nullable String cape, @Nullable String effect) {
	}

	/** {@code remote} is null for players who don't use Lumina. */
	private record Known(@Nullable Remote remote, long at) {
	}

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Lumina Online");
		t.setDaemon(true);
		return t;
	});
	private static final Map<UUID, Known> known = new ConcurrentHashMap<>();
	private static final Set<UUID> pending = ConcurrentHashMap.newKeySet();

	private static volatile @Nullable UUID user;
	private static volatile @Nullable String token;
	private static volatile @Nullable UUID tokenFor;
	private static volatile long tokenUntil;
	private static volatile boolean signingIn;
	private static volatile int failures;
	private static volatile long pauseUntil;

	private static volatile @Nullable String synced;
	private static volatile long syncedAt;
	private static volatile boolean syncing;
	private static @Nullable String wanted;
	private static long wantedSince;

	private static double requests = BURST;
	private static long refilled;
	private static int ticks;

	private LuminaOnline() {
	}

	private static void forget() {
		user = null;
		token = null;
		tokenFor = null;
		synced = null;
		syncedAt = 0;
		failures = 0;
		pauseUntil = 0;
		known.clear();
		pending.clear();
	}

	private static String apiUrl() {
		String url = System.getProperty("lumina.api", "https://lumina-api.lumina-cc1.workers.dev");
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	// ------------------------------------------------------------------
	// what the rest of the client asks
	// ------------------------------------------------------------------

	/** Whether this player gets the Lumina mark: always you, and every other Lumina player. */
	public static boolean showsLogo(UUID id) {
		Minecraft mc = Minecraft.getInstance();
		if (id.equals(mc.getUser().getProfileId())) return true;
		LocalPlayer me = mc.player;
		if (me != null && id.equals(me.getUUID())) return true;
		Known k = known.get(id);
		return k != null && k.remote != null;
	}

	/** What another Lumina player wears, or null if they don't use Lumina (or nobody asked yet). */
	public static @Nullable Remote remote(UUID id) {
		Known k = known.get(id);
		return k == null ? null : k.remote;
	}

	// ------------------------------------------------------------------
	// client tick
	// ------------------------------------------------------------------

	public static void tick(Minecraft mc) {
		if (!ENABLED) return;
		var settings = Lumina.settings();
		if (settings == null || !settings.online.on()) {
			// switched off: forget everything; nothing is sent until it is on again
			if (user != null) forget();
			return;
		}
		long now = Util.getMillis();
		UUID id = mc.getUser().getProfileId();
		if (!id.equals(user)) {
			// first tick, or the account was switched in the main menu
			user = id;
			token = null;
			tokenFor = null;
			synced = null;
			syncedAt = 0;
			failures = 0;
			pauseUntil = 0;
		}
		ticks++;
		if (ticks % 1200 == 0) prune(now);
		if (token == null || !id.equals(tokenFor) || now > tokenUntil) {
			if (!signingIn && now >= pauseUntil) signIn(mc, id);
			return;
		}
		syncOwn(now);
		if (ticks % 10 == 0) lookUp(mc, now);
	}

	// ------------------------------------------------------------------
	// sign-in
	// ------------------------------------------------------------------

	private static void signIn(Minecraft mc, UUID id) {
		signingIn = true;
		var u = mc.getUser();
		String name = u.getName();
		EXEC.execute(() -> {
			try {
				JsonObject r;
				if (DEV_LOGIN) {
					JsonObject body = new JsonObject();
					body.addProperty("name", name);
					body.addProperty("uuid", id.toString());
					r = send("POST", "/v1/auth/dev", body, null);
				} else {
					r = signInWithPlayerKey(mc, id, name);
				}
				if (!id.equals(user)) return; // switched accounts meanwhile
				long validMs = r.get("exp").getAsLong() * 1000 - System.currentTimeMillis();
				tokenUntil = Util.getMillis() + validMs - 60_000;
				tokenFor = id;
				token = r.get("token").getAsString();
				failures = 0;
				Lumina.LOG.info("Signed in to Lumina online as {}", name);
			} catch (Exception e) {
				int n = ++failures;
				// offline accounts land here every time: try again later, less and less often
				pauseUntil = Util.getMillis() + Math.min(30 * 60_000L, 30_000L << Math.min(n - 1, 6));
				if (n == 1 || Lumina.DEV) Lumina.LOG.info("Lumina online sign-in failed: {}", message(e));
			} finally {
				signingIn = false;
			}
		});
	}

	/**
	 * Proves who you are with the player key Mojang gives every account (the one behind signed chat):
	 * Lumina's server checks Mojang's signature on the public key and our signature on its challenge.
	 * The private key and your session token stay on this PC; nothing is sent to Mojang.
	 */
	private static JsonObject signInWithPlayerKey(Minecraft mc, UUID id, String name) throws Exception {
		ProfileKeyPair keys = mc.getProfileKeyPairManager().prepareKeyPair().get(30, TimeUnit.SECONDS)
			.orElseThrow(() -> new IOException("no Mojang player key (offline account?)"));
		ProfilePublicKey.Data key = keys.publicKey().data();
		JsonObject body = new JsonObject();
		body.addProperty("uuid", id.toString());
		JsonObject challenge = send("POST", "/v1/auth/challenge", body, null);
		Signature signer = Signature.getInstance("SHA256withRSA");
		signer.initSign(keys.privateKey());
		signer.update(("lumina-auth|" + challenge.get("challenge").getAsString()).getBytes(StandardCharsets.UTF_8));
		Base64.Encoder b64 = Base64.getEncoder();
		body.addProperty("name", name);
		body.addProperty("nonce", challenge.get("nonce").getAsString());
		body.addProperty("exp", challenge.get("exp").getAsLong());
		body.addProperty("publicKey", b64.encodeToString(key.key().getEncoded()));
		body.addProperty("keySignature", b64.encodeToString(key.keySignature()));
		body.addProperty("expiresAt", key.expiresAt().toEpochMilli());
		body.addProperty("signature", b64.encodeToString(signer.sign()));
		return send("POST", "/v1/auth/key", body, null);
	}

	// ------------------------------------------------------------------
	// your own profile
	// ------------------------------------------------------------------

	/** What you wear, as the server stores it: "cape\neffect", each part may be empty. */
	private static String ownCosmetics() {
		CosmeticsModule m = Lumina.modules().get(CosmeticsModule.class);
		if (m == null) return "\n";
		// only capes that ship with Lumina: everyone has those files
		CapeLibrary.Entry cape = m.equippedCape();
		String capeId = cape != null && cape.bundled() ? cape.id() : "";
		ParticleEffects.Effect e = m.enabled() ? m.particles.effect() : null;
		String effect = e == null ? "" : e.tinted ? e.id() + ":" + String.format("%06x", m.effectColor.color() & 0xFFFFFF) : e.id();
		return capeId + "\n" + effect;
	}

	private static void syncOwn(long now) {
		String want = ownCosmetics();
		if (!want.equals(wanted)) {
			wanted = want;
			wantedSince = now;
		}
		// wait until clicking through the Cosmetics page has settled; the heartbeat keeps you listed
		boolean changed = !want.equals(synced) && now - wantedSince >= SETTLE_MS;
		boolean heartbeat = now - syncedAt >= HEARTBEAT_MS;
		if (!(changed || heartbeat) || syncing || now < pauseUntil) return;
		syncing = true;
		String t = token;
		EXEC.execute(() -> {
			try {
				String[] parts = want.split("\n", -1);
				JsonObject body = new JsonObject();
				body.addProperty("cape", parts[0].isEmpty() ? null : parts[0]);
				body.addProperty("effect", parts[1].isEmpty() ? null : parts[1]);
				send("PUT", "/v1/me", body, t);
				synced = want;
				syncedAt = Util.getMillis();
				if (Lumina.DEV) Lumina.LOG.info("[dev] online: profile synced {}", want.replace('\n', '|'));
			} catch (Exception e) {
				failed(e);
			} finally {
				syncing = false;
			}
		});
	}

	// ------------------------------------------------------------------
	// who else uses Lumina
	// ------------------------------------------------------------------

	private static void lookUp(Minecraft mc, long now) {
		if (now < pauseUntil || !pending.isEmpty()) return;
		requests = Math.min(BURST, requests + (now - refilled) / (double) REFILL_MS);
		refilled = now;
		if (requests < 1) return;
		LocalPlayer me = mc.player;
		if (me == null || mc.level == null) return;

		LinkedHashSet<UUID> batch = new LinkedHashSet<>();
		// players in view first, closest first
		List<AbstractClientPlayer> near = new ArrayList<>(mc.level.players());
		near.sort(Comparator.comparingDouble(p -> p.distanceToSqr(me)));
		for (AbstractClientPlayer p : near) {
			if (batch.size() >= BATCH) break;
			consider(batch, p.getUUID(), now);
		}
		// then the tab list, only as far as it is shown
		ClientPacketListener connection = mc.getConnection();
		if (batch.size() < BATCH && connection != null) {
			var shown = connection.getListedOnlinePlayers().stream().sorted(PlayerTabOverlayAccessor.lumina$order()).limit(TAB_LIST).toList();
			for (var info : shown) {
				if (batch.size() >= BATCH) break;
				consider(batch, info.getProfile().id(), now);
			}
		}
		if (batch.isEmpty()) return;

		requests -= 1;
		List<UUID> ids = new ArrayList<>(batch);
		pending.addAll(ids);
		String t = token;
		EXEC.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				JsonArray arr = new JsonArray();
				for (UUID id : ids) arr.add(id.toString());
				body.add("uuids", arr);
				JsonObject players = send("POST", "/v1/lookup", body, t).getAsJsonObject("players");
				long at = Util.getMillis();
				int found = 0;
				for (UUID id : ids) {
					JsonElement e = players.get(id.toString());
					Remote r = null;
					if (e != null && e.isJsonObject()) {
						r = new Remote(string(e.getAsJsonObject(), "cape"), string(e.getAsJsonObject(), "effect"));
						found++;
					}
					known.put(id, new Known(r, at));
				}
				if (Lumina.DEV) Lumina.LOG.info("[dev] online: looked up {} players, {} use Lumina {}", ids.size(), found, players);
			} catch (Exception e) {
				failed(e);
			} finally {
				ids.forEach(pending::remove);
			}
		});
	}

	private static void consider(Set<UUID> batch, UUID id, long now) {
		if (id.equals(user) || pending.contains(id)) return;
		// Mojang accounts only: NPCs (version 2) and offline-mode players (version 3) can't be Lumina players
		if (!Lumina.DEV && id.version() != 4) return;
		Known k = known.get(id);
		if (k != null && now - k.at < CACHE_MS) return;
		batch.add(id);
	}

	private static void prune(long now) {
		long keep = known.size() > MAX_CACHE ? CACHE_MS : 3 * CACHE_MS;
		known.values().removeIf(k -> now - k.at > keep);
	}

	// ------------------------------------------------------------------
	// http
	// ------------------------------------------------------------------

	private static final class ApiException extends IOException {
		final int status;

		ApiException(int status, String body) {
			super("HTTP " + status + " " + body);
			this.status = status;
		}
	}

	private static void failed(Exception e) {
		if (e instanceof ApiException api && api.status == 401) {
			// token no longer accepted: sign in again shortly
			token = null;
			pauseUntil = Util.getMillis() + 5_000;
		} else {
			pauseUntil = Util.getMillis() + 60_000;
		}
		if (Lumina.DEV) Lumina.LOG.info("[dev] online: request failed: {}", message(e));
	}

	private static JsonObject send(String method, String path, @Nullable JsonObject body, @Nullable String auth) throws IOException, InterruptedException {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API + path))
			.timeout(Duration.ofSeconds(10))
			.header("user-agent", "Lumina/" + Lumina.VERSION)
			.header("content-type", "application/json")
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString()));
		if (auth != null) b.header("authorization", "Bearer " + auth);
		HttpResponse<String> res = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() != 200) throw new ApiException(res.statusCode(), res.body());
		return JsonParser.parseString(res.body()).getAsJsonObject();
	}

	private static @Nullable String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	private static String message(Throwable e) {
		return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
	}
}
