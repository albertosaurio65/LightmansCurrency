package io.github.lightman314.lightmanscurrency.client.gui.widget.button;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.NonNullSupplier;

@OnlyIn(Dist.CLIENT)
public class PlainButton extends Button{
	
	private ResourceLocation buttonResource;
	private NonNullSupplier<Pair<Integer,Integer>> resourceSource;
	
	
	public PlainButton(int x, int y, int sizeX, int sizeY, OnPress pressable, ResourceLocation buttonResource, int resourceX, int resourceY) {
		this(x, y, sizeX, sizeY, pressable, buttonResource, () -> Pair.of(resourceX, resourceY));
	}
	
	public PlainButton(int x, int y, int sizeX, int sizeY, OnPress pressable, ResourceLocation buttonResource, NonNullSupplier<Pair<Integer, Integer>> resourceSource)
	{
		super(x, y, sizeX, sizeY, Component.empty(), pressable);
		this.buttonResource = buttonResource;
		this.resourceSource = resourceSource;
	}
	
	public void setResource(ResourceLocation buttonResource, int resourceX, int resourceY) { this.setResource(buttonResource, () -> Pair.of(resourceX, resourceY)); }
	
	public void setResource(ResourceLocation buttonResource, NonNullSupplier<Pair<Integer, Integer>> resourceSource)
	{
		this.buttonResource = buttonResource;
		this.resourceSource = resourceSource;
	}
	
	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
	{
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, this.buttonResource);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        int offset = this.isHovered ? this.height : 0;
        if(!this.active)
        	RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, 1.0F);
        Pair<Integer,Integer> resource = this.resourceSource.get();
        this.blit(poseStack, this.x, this.y, resource.getFirst(), resource.getSecond() + offset, this.width, this.height);
		
	}

}
