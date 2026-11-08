package dev.lumina.client.feature.account;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.FriendsService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import dev.lumina.client.mixin.MinecraftAccountAccessor;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.util.Util;

/** Makes the running game sign in as another account: everything vanilla sets up once at startup. */
public final class AccountSwitcher {
	public record Prepared(AccountBridge.Credentials credentials, User user, UserApiService api, FriendsService friends) {
	}

	private AccountSwitcher() {
	}

	/** The part that may touch the network; on a background thread. */
	public static Prepared prepare(AccountBridge.Credentials c) {
		User user = new User(c.name(), c.uuid(), c.accessToken(), c.xuid().isEmpty() ? Optional.empty() : Optional.of(c.xuid()), Optional.empty());
		MinecraftServicesDiscoveryService service = MinecraftServicesDiscoveryService.create(Minecraft.getInstance().getProxy(), c.microsoft());
		UserApiService api = c.microsoft() ? service.createUserApiService(c.accessToken()) : UserApiService.OFFLINE;
		return new Prepared(c, user, api, service.createFriendsService(c.accessToken()));
	}

	/** Render thread. Only from the main menu: nothing is connected to a world or server here. */
	public static void apply(Prepared p) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftAccountAccessor game = (MinecraftAccountAccessor) mc;
		User user = p.user();
		UserApiService api = p.api();
		boolean online = p.credentials().microsoft();
		game.lumina$setUser(user);
		game.lumina$setProfileFuture(online
			? CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(user.getProfileId(), true), Util.nonCriticalIoPool())
			: CompletableFuture.completedFuture(null));
		game.lumina$setUserApiService(api);
		game.lumina$setUserPropertiesFuture(CompletableFuture.supplyAsync(() -> {
			try {
				return api.fetchProperties();
			} catch (AuthenticationException e) {
				return UserApiService.OFFLINE_PROPERTIES;
			}
		}, Util.nonCriticalIoPool()));
		RemoteFriendListUpdateHandler oldFriends = game.lumina$getRemoteFriendListUpdateHandler();
		RemoteFriendListUpdateHandler friends = new RemoteFriendListUpdateHandler(p.friends(), mc);
		PlayerSocialManager social = new PlayerSocialManager(mc, api, p.friends(), friends);
		game.lumina$setRemoteFriendListUpdateHandler(friends);
		game.lumina$setPlayerSocialManager(social);
		oldFriends.close();
		if (social.isFriendListEnabled()) friends.start();
		game.lumina$setProfileKeyPairManager(online
			? ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath())
			: ProfileKeyPairManager.EMPTY_KEY_MANAGER);
		game.lumina$setReportingContext(ReportingContext.create(ReportEnvironment.local(), api));
		ClientTelemetryManager old = mc.getTelemetryManager();
		game.lumina$setTelemetryManager(new ClientTelemetryManager(mc, api, user));
		old.close();
	}
}
