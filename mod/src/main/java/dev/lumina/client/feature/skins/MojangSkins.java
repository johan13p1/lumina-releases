package dev.lumina.client.feature.skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;

/**
 * The official Minecraft services API for your profile's skin, used with the access token of the
 * running game (the Microsoft login from the launcher). Nothing else is sent anywhere.
 */
final class MojangSkins {
	private static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";
	private static final String SKINS = PROFILE + "/skins";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

	/** The active skin of the profile: its download URL and whether it uses slim arms. */
	record Profile(String name, @Nullable String skinUrl, boolean slim) {
	}

	/** A failed request with a message that can be shown to the player. */
	static final class SkinException extends RuntimeException {
		SkinException(String message) {
			super(message);
		}
	}

	private MojangSkins() {
	}

	static CompletableFuture<Profile> profile(String token) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE)).timeout(Duration.ofSeconds(20))
			.header("Authorization", "Bearer " + token).GET().build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(MojangSkins::parseProfile);
	}

	static CompletableFuture<Profile> upload(String token, byte[] png, boolean slim) {
		String boundary = "----LuminaSkin" + UUID.randomUUID().toString().replace("-", "");
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		try {
			body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic") + "\r\n").getBytes(StandardCharsets.UTF_8));
			body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			body.write(png);
			body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS)).timeout(Duration.ofSeconds(30))
			.header("Authorization", "Bearer " + token)
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(MojangSkins::parseProfile);
	}

	static CompletableFuture<byte[]> download(String url) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url.replace("http://", "https://"))).timeout(Duration.ofSeconds(20)).GET().build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
			if (r.statusCode() != 200) throw new SkinException("Could not download the skin (HTTP " + r.statusCode() + ")");
			return r.body();
		});
	}

	private static Profile parseProfile(HttpResponse<String> response) {
		int status = response.statusCode();
		if (status == 401 || status == 403) throw new SkinException("Your login has expired. Restart the game from the launcher.");
		if (status == 404) throw new SkinException("This account doesn't own Minecraft: Java Edition.");
		if (status == 429) throw new SkinException("Too many skin changes. Try again in a minute.");
		if (status / 100 != 2) throw new SkinException(errorMessage(response.body(), status));
		JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
		String name = json.has("name") ? json.get("name").getAsString() : "";
		String url = null;
		boolean slim = false;
		JsonArray skins = json.has("skins") ? json.getAsJsonArray("skins") : new JsonArray();
		for (JsonElement el : skins) {
			JsonObject skin = el.getAsJsonObject();
			if (!"ACTIVE".equalsIgnoreCase(skin.has("state") ? skin.get("state").getAsString() : "")) continue;
			url = skin.has("url") ? skin.get("url").getAsString() : null;
			slim = skin.has("variant") && "SLIM".equalsIgnoreCase(skin.get("variant").getAsString());
		}
		return new Profile(name, url, slim);
	}

	private static String errorMessage(String body, int status) {
		try {
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			for (String key : new String[] {"errorMessage", "error_description", "error", "details"}) {
				if (json.has(key) && json.get(key).isJsonPrimitive()) return json.get(key).getAsString();
			}
		} catch (RuntimeException ignored) {
		}
		return "The skin server answered with HTTP " + status + ".";
	}
}
