package dev.lumina.client.feature.skins;

import dev.lumina.client.Lumina;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * Changes your real Minecraft skin (the one everybody sees) through the official API, with the
 * login the launcher started the game with. Before the first change your current skin is saved
 * into the library, so you can always go back to it.
 *
 * <p>After a change the new skin shows on your own player right away; servers pick it up when you
 * join them again.
 */
public final class SkinChanger {
	private static final SkinChanger INSTANCE = new SkinChanger();

	public final SkinLibrary library = new SkinLibrary();
	private boolean loaded;
	private boolean profileLoaded;
	private boolean busy;
	private @Nullable String activeHash;
	private PlayerModelType activeModel = PlayerModelType.WIDE;
	private @Nullable Identifier overrideTexture;
	private PlayerModelType overrideModel = PlayerModelType.WIDE;
	private @Nullable String message;
	private boolean messageError;
	private long messageUntil;

	private SkinChanger() {
	}

	public static SkinChanger get() {
		return INSTANCE;
	}

	/** Called when the Skin Changer page opens: reads the library and, once, your current skin. */
	public void open() {
		if (!loaded) {
			library.reload();
			loaded = true;
		}
		if (!profileLoaded && !busy && online()) loadProfile();
	}

	/** Offline accounts (launcher "Offline" profiles) have no token the skin API accepts. */
	public static boolean online() {
		String token = Minecraft.getInstance().getUser().getAccessToken();
		return token != null && token.length() > 20;
	}

	public boolean busy() {
		return busy;
	}

	public boolean isActive(SkinLibrary.Entry entry) {
		return entry.hash().equals(activeHash) && library.model(entry) == activeModel;
	}

	public SkinLibrary.@Nullable Entry activeEntry() {
		return library.byHash(activeHash);
	}

	public @Nullable String message() {
		return message != null && Util.getMillis() < messageUntil ? message : null;
	}

	public boolean messageIsError() {
		return messageError;
	}

	private void say(String text, boolean error, long ms) {
		message = text;
		messageError = error;
		messageUntil = Util.getMillis() + ms;
	}

	private void loadProfile() {
		busy = true;
		say("Loading your current skin…", false, 60_000);
		String token = Minecraft.getInstance().getUser().getAccessToken();
		MojangSkins.profile(token)
			.thenCompose(p -> p.skinUrl() == null
				? CompletableFuture.completedFuture(new Current(p, null))
				: MojangSkins.download(p.skinUrl()).thenApply(png -> new Current(p, png)))
			.whenComplete((current, error) -> Minecraft.getInstance().execute(() -> {
				busy = false;
				if (error != null) {
					say(describe(error), true, 8000);
					return;
				}
				profileLoaded = true;
				message = null;
				if (current.png == null) return; // default skin
				try {
					SkinLibrary.Entry entry = library.save(current.png, current.profile.name() + " " + LocalDate.now());
					PlayerModelType model = current.profile.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE;
					library.setModel(entry, model);
					activeHash = entry.hash();
					activeModel = model;
				} catch (IOException e) {
					Lumina.LOG.warn("Could not save the current skin", e);
				}
			}));
	}

	private record Current(MojangSkins.Profile profile, byte @Nullable [] png) {
	}

	/** Uploads the skin as your new Minecraft skin. */
	public void apply(SkinLibrary.Entry entry) {
		if (busy) return;
		if (!online()) {
			say("Changing skins needs a Microsoft account.", true, 5000);
			return;
		}
		SkinLibrary.Texture texture = library.texture(entry);
		if (texture == null) {
			say("This file is not a valid skin.", true, 5000);
			return;
		}
		byte[] png;
		try {
			png = library.read(entry);
		} catch (IOException e) {
			say("Could not read " + entry.file().getFileName(), true, 5000);
			return;
		}
		PlayerModelType model = library.model(entry);
		busy = true;
		say("Uploading skin…", false, 60_000);
		MojangSkins.upload(Minecraft.getInstance().getUser().getAccessToken(), png, model == PlayerModelType.SLIM)
			.whenComplete((profile, error) -> Minecraft.getInstance().execute(() -> {
				busy = false;
				if (error != null) {
					say(describe(error), true, 8000);
					return;
				}
				activeHash = entry.hash();
				activeModel = model;
				overrideTexture = texture.id();
				overrideModel = model;
				say("Skin changed! Servers show it after you rejoin.", false, 6000);
			}));
	}

	private static String describe(Throwable error) {
		Throwable e = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		if (e instanceof MojangSkins.SkinException) return e.getMessage();
		if (e instanceof ConnectException || e instanceof HttpTimeoutException || e instanceof UnresolvedAddressException
			|| e.getCause() instanceof UnresolvedAddressException) return "No connection to the Minecraft skin server.";
		Lumina.LOG.warn("Skin request failed", e);
		return "Something went wrong: " + e.getClass().getSimpleName();
	}

	/** Your own skin as the game should show it: the one you just uploaded, until the game restarts. */
	public PlayerSkin patch(PlayerSkin original) {
		Identifier tex = overrideTexture;
		if (tex == null) return original;
		return new PlayerSkin(new ClientAsset.ResourceTexture(tex, tex), original.cape(), original.elytra(), overrideModel, original.secure());
	}

	/** A skin for previews that keeps your cape. */
	public static PlayerSkin preview(PlayerSkin base, Identifier texture, PlayerModelType model) {
		return new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), base.cape(), base.elytra(), model, base.secure());
	}
}
