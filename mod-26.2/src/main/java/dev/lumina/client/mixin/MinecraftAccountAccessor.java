package dev.lumina.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What vanilla only sets once at startup and that belongs to the signed-in account. */
@Mixin(Minecraft.class)
public interface MinecraftAccountAccessor {
	@Mutable
	@Accessor("user")
	void lumina$setUser(User user);

	@Mutable
	@Accessor("profileFuture")
	void lumina$setProfileFuture(CompletableFuture<ProfileResult> future);

	@Mutable
	@Accessor("userApiService")
	void lumina$setUserApiService(UserApiService service);

	@Mutable
	@Accessor("userPropertiesFuture")
	void lumina$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);

	@Mutable
	@Accessor("playerSocialManager")
	void lumina$setPlayerSocialManager(PlayerSocialManager manager);

	@Accessor("remoteFriendListUpdateHandler")
	RemoteFriendListUpdateHandler lumina$getRemoteFriendListUpdateHandler();

	@Mutable
	@Accessor("remoteFriendListUpdateHandler")
	void lumina$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler handler);

	@Mutable
	@Accessor("profileKeyPairManager")
	void lumina$setProfileKeyPairManager(ProfileKeyPairManager manager);

	@Accessor("reportingContext")
	void lumina$setReportingContext(ReportingContext context);

	@Mutable
	@Accessor("telemetryManager")
	void lumina$setTelemetryManager(ClientTelemetryManager manager);
}
