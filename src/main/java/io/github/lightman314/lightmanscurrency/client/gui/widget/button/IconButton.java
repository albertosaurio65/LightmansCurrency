package io.github.lightman314.lightmanscurrency.client.gui.widget.button;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class IconButton extends Button{
	
	private ResourceLocation iconResource;
	private int resourceX;
	private int resourceY;
	
	public IconButton(int x, int y, OnPress pressable, ResourceLocation iconResource, int resourceX, int resourceY)
	{
		super(x,y,20,20, TextComponent.EMPTY, pressable);
		this.setResource(iconResource, resourceX, resourceY);
	}
	
	public void setResource(ResourceLocation iconResource, int resourceX, int resourceY)
	{
		this.iconResource = iconResource;
		this.resourceX = resourceX;
		this.resourceY = resourceY;
	}
	
	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTicks)
	{
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderTexture(0, WIDGETS_LOCATION);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        int offset = this.getYImage(this.isHovered());
        this.blit(poseStack, this.x, this.y, 0, 46 + offset * 20, this.width / 2, this.height);
        this.blit(poseStack, this.x + this.width / 2, this.y, 200 - this.width / 2, 46 + offset * 20, this.width / 2, this.height);
        if(!this.active)
            RenderSystem.setShaderColor(0.5F, 0.5F, 0.5F, 1.0F);
        //Minecraft.getInstance().getTextureManager().bindForSetup(this.iconResource);
        RenderSystem.setShaderTexture(0, this.iconResource);
        this.blit(poseStack, this.x + 2, this.y + 2, this.resourceX, this.resourceY, 16, 16);
		
	}

}
