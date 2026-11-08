package dev.lumina.client.feature.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jspecify.annotations.Nullable;

/**
 * The launcher's accounts, while the game runs. The launcher keeps the sign-ins and hands out a
 * token when the game switches account; nothing about an account is stored on the game's side.
 * Only there when the launcher started the game (the {@code lumina.accounts} property).
 */
public final class AccountBridge {
	public record Account(String id, boolean microsoft, String name, UUID uuid) {
	}

	public record Accounts(@Nullable String selected, List<Account> accounts) {
	}

	public record Credentials(boolean microsoft, String name, UUID uuid, String accessToken, String xuid) {
	}

	private static final String BASE = System.getProperty("lumina.accounts", "");
	private static final Duration QUICK = Duration.ofSeconds(30);
	/** Someone is typing their password in the sign-in window. */
	private static final Duration SIGN_IN = Duration.ofMinutes(15);
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private AccountBridge() {
	}

	public static boolean available() {
		return !BASE.isEmpty();
	}

	public static CompletableFuture<Accounts> list() {
		return call("accounts", new JsonObject(), QUICK).thenApply(AccountBridge::accounts);
	}

	public static CompletableFuture<Accounts> addMicrosoft() {
		return call("add", new JsonObject(), SIGN_IN).thenApply(AccountBridge::accounts);
	}

	public static CompletableFuture<Accounts> remove(String id) {
		JsonObject body = new JsonObject();
		body.addProperty("id", id);
		return call("remove", body, QUICK).thenApply(AccountBridge::accounts);
	}

	public static CompletableFuture<Credentials> use(String id) {
		JsonObject body = new JsonObject();
		body.addProperty("id", id);
		return call("use", body, QUICK).thenApply(o -> new Credentials(
			"msa".equals(o.get("type").getAsString()),
			o.get("name").getAsString(),
			uuid(o.get("uuid").getAsString()),
			o.get("accessToken").getAsString(),
			o.has("xuid") ? o.get("xuid").getAsString() : ""));
	}

	/** The account's skin picture as PNG bytes, or null if there is none. */
	public static CompletableFuture<byte @Nullable []> skin(UUID uuid) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid.toString().replace("-", ""));
		return call("skin", body, QUICK).thenApply(o -> {
			JsonElement data = o.get("data");
			if (data == null || data.isJsonNull()) return null;
			String url = data.getAsString();
			return Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
		});
	}

	private static CompletableFuture<JsonObject> call(String action, JsonObject body, Duration timeout) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/" + action))
			.timeout(timeout)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
			.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.exceptionally(e -> {
				Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
				if (cause instanceof ConnectException) throw new CompletionException(new IllegalStateException("The launcher is closed, so accounts can't be changed right now"));
				throw new CompletionException(cause);
			})
			.thenApply(response -> {
				JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
				if (response.statusCode() != 200) throw new CompletionException(new IllegalStateException(json.has("error") ? json.get("error").getAsString() : "The launcher answered with an error"));
				return json;
			});
	}

	private static Accounts accounts(JsonObject json) {
		List<Account> list = new ArrayList<>();
		JsonArray array = json.getAsJsonArray("accounts");
		for (JsonElement e : array) {
			JsonObject a = e.getAsJsonObject();
			list.add(new Account(a.get("id").getAsString(), "msa".equals(a.get("type").getAsString()), a.get("name").getAsString(), uuid(a.get("uuid").getAsString())));
		}
		JsonElement selected = json.get("selected");
		return new Accounts(selected == null || selected.isJsonNull() ? null : selected.getAsString(), list);
	}

	/** Mojang writes UUIDs without dashes, offline accounts have them. */
	private static UUID uuid(String text) {
		String hex = text.replace("-", "");
		return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
	}
}
