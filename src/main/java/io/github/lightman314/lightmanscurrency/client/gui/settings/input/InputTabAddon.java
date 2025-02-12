package io.github.lightman314.lightmanscurrency.client.gui.settings.input;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.lightman314.lightmanscurrency.client.gui.screen.TraderSettingsScreen;

public abstract class InputTabAddon {
	
	public abstract void onInit(TraderSettingsScreen screen);
	
	public abstract void preRender(TraderSettingsScreen screen, PoseStack pose, int mouseX, int mouseY, float partialTicks);
	public abstract void postRender(TraderSettingsScreen screen, PoseStack pose, int mouseX, int mouseY, float partialTicks);
	
	public abstract void tick(TraderSettingsScreen screen);
	
	public abstract void onClose(TraderSettingsScreen screen);
	
}
