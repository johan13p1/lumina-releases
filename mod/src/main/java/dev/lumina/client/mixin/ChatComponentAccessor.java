package dev.lumina.client.mixin;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("allMessages")
	List<GuiMessage> lumina$allMessages();

	@Accessor("trimmedMessages")
	List<GuiMessage.Line> lumina$trimmedMessages();
}
