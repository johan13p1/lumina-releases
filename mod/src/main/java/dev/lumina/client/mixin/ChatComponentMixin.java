package dev.lumina.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.lumina.client.Lumina;
import dev.lumina.client.feature.utility.ChatModule;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.client.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Unique
	private String lumina$lastText;
	@Unique
	private int lumina$repeat = 1;

	@ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
		at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
	private float lumina$background(float original) {
		ChatModule m = Lumina.modules().get(ChatModule.class);
		return m != null && m.enabled() ? m.background.getFloat() : original;
	}

	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
		at = @At("HEAD"), argsOnly = true)
	private Component lumina$decorate(Component message, Component original, MessageSignature signature, GuiMessageTag tag) {
		ChatModule m = Lumina.modules().get(ChatModule.class);
		if (m == null || !m.enabled()) return message;
		Component result = message;
		if (m.stack.on()) {
			String text = message.getString();
			ChatComponentAccessor self = (ChatComponentAccessor) this;
			List<GuiMessage> all = self.lumina$allMessages();
			if (text.equals(lumina$lastText) && !all.isEmpty() && signature == null) {
				lumina$repeat++;
				all.removeFirst();
				List<GuiMessage.Line> lines = self.lumina$trimmedMessages();
				if (!lines.isEmpty()) {
					lines.removeFirst();
					while (!lines.isEmpty() && !lines.getFirst().endOfEntry()) lines.removeFirst();
				}
				result = message.copy().append(Component.literal(" x" + lumina$repeat).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x8E8E96))));
			} else {
				lumina$repeat = 1;
			}
			lumina$lastText = text;
		}
		return m.decorate(result);
	}

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int lumina$history(int original) {
		ChatModule m = Lumina.modules().get(ChatModule.class);
		return m != null ? m.historySize() : original;
	}
}
