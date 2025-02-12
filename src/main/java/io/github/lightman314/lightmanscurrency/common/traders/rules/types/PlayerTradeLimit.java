package io.github.lightman314.lightmanscurrency.common.traders.rules.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.base.Supplier;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.client.gui.screen.TradeRuleScreen;
import io.github.lightman314.lightmanscurrency.client.gui.widget.TimeInputWidget;
import io.github.lightman314.lightmanscurrency.client.gui.widget.button.icon.IconData;
import io.github.lightman314.lightmanscurrency.client.util.IconAndButtonUtil;
import io.github.lightman314.lightmanscurrency.client.util.TextInputUtil;
import io.github.lightman314.lightmanscurrency.client.util.TextRenderUtil;
import io.github.lightman314.lightmanscurrency.common.traders.rules.TradeRule;
import io.github.lightman314.lightmanscurrency.events.TradeEvent.PostTradeEvent;
import io.github.lightman314.lightmanscurrency.events.TradeEvent.PreTradeEvent;
import io.github.lightman314.lightmanscurrency.util.MathUtil;
import io.github.lightman314.lightmanscurrency.util.TimeUtil;
import io.github.lightman314.lightmanscurrency.util.TimeUtil.TimeData;
import io.github.lightman314.lightmanscurrency.util.TimeUtil.TimeUnit;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class PlayerTradeLimit extends TradeRule{
	
	public static final ResourceLocation OLD_TYPE = new ResourceLocation(LightmansCurrency.MODID, "tradelimit");
	public static final ResourceLocation TYPE = new ResourceLocation(LightmansCurrency.MODID, "player_trade_limit");
	
	private int limit = 1;
	public int getLimit() { return this.limit; }
	public void setLimit(int newLimit) { this.limit = newLimit; }
	
	private long timeLimit = 0;
	private boolean enforceTimeLimit() { return this.timeLimit > 0; }
	public long getTimeLimit() { return this.timeLimit; }
	public void setTimeLimit(int newValue) { this.timeLimit = newValue; }
	
	Map<UUID,List<Long>> memory = new HashMap<>();
	public void resetMemory() { this.memory.clear(); }
	
	public PlayerTradeLimit() { super(TYPE); }
	
	@Override
	public void beforeTrade(PreTradeEvent event) {
		
		int tradeCount = getTradeCount(event.getPlayerReference().id);
		if(tradeCount >= this.limit)
		{
			if(this.enforceTimeLimit())
				event.addDenial(Component.translatable("traderule.lightmanscurrency.tradelimit.denial.timed", tradeCount, new TimeUtil.TimeData(this.getTimeLimit()).getString()));
			else
				event.addDenial(Component.translatable("traderule.lightmanscurrency.tradelimit.denial", tradeCount));
			event.addDenial(Component.translatable("traderule.lightmanscurrency.tradelimit.denial.limit", this.limit));
		}
		else
		{
			if(this.enforceTimeLimit())
				event.addHelpful(Component.translatable("traderule.lightmanscurrency.tradelimit.info.timed", tradeCount, this.limit, new TimeUtil.TimeData(this.getTimeLimit()).getString()));
			else
				event.addHelpful(Component.translatable("traderule.lightmanscurrency.tradelimit.info", tradeCount, this.limit));
		}
	}

	@Override
	public void afterTrade(PostTradeEvent event) {
		
		this.addEvent(event.getPlayerReference().id, TimeUtil.getCurrentTime());
		
		this.clearExpiredData();
		
		event.markDirty();
		
	}
	
	private void addEvent(UUID player, Long time)
	{
		List<Long> eventTimes = new ArrayList<>();
		if(this.memory.containsKey(player))
			eventTimes = this.memory.get(player);
		eventTimes.add(time);
		this.memory.put(player, eventTimes);
	}
	
	private void clearExpiredData()
	{
		if(!this.enforceTimeLimit())
			return;
		List<UUID> emptyEntries = new ArrayList<>();
		this.memory.forEach((id, eventTimes) ->{
			for(int i = 0; i < eventTimes.size(); i++)
			{
				if(!TimeUtil.compareTime(this.timeLimit, eventTimes.get(i)))
				{
					eventTimes.remove(i);
					i--;
				}
			}
			if(eventTimes.size() <= 0)
				emptyEntries.add(id);
		});
		emptyEntries.forEach(id -> this.memory.remove(id));
	}
	
	private int getTradeCount(UUID playerID)
	{
		int count = 0;
		if(this.memory.containsKey(playerID))
		{
			List<Long> eventTimes = this.memory.get(playerID);
			if(!this.enforceTimeLimit())
				return eventTimes.size();
			for(int i = 0; i < eventTimes.size(); i++)
			{
				if(TimeUtil.compareTime(this.timeLimit, eventTimes.get(i)))
					count++;
			}
		}
		return count;
	}
	
	@Override
	protected void saveAdditional(CompoundTag compound) {
		
		compound.putInt("Limit", this.limit);
		ListTag memoryList = new ListTag();
		this.memory.forEach((id, eventTimes) ->{
			CompoundTag thisMemory = new CompoundTag();
			thisMemory.putUUID("id", id);
			thisMemory.putLongArray("times", eventTimes);
			memoryList.add(thisMemory);
		});
		compound.put("Memory", memoryList);
		compound.putLong("ForgetTime", this.timeLimit);
	}
	
	@Override
	public JsonObject saveToJson(JsonObject json) {
		json.addProperty("Limit", this.limit);
		if(this.enforceTimeLimit())
			json.addProperty("ForgetTime", this.timeLimit);
		return json;
	}

	@Override
	protected void loadAdditional(CompoundTag compound) {
		
		if(compound.contains("Limit", Tag.TAG_INT))
			this.limit = compound.getInt("Limit");
		if(compound.contains("Memory", Tag.TAG_LIST))
		{
			this.memory.clear();
			ListTag memoryList = compound.getList("Memory", Tag.TAG_COMPOUND);
			for(int i = 0; i < memoryList.size(); i++)
			{
				CompoundTag thisMemory = memoryList.getCompound(i);
				UUID id = null;
				List<Long> eventTimes = new ArrayList<>();
				if(thisMemory.contains("id"))
					id = thisMemory.getUUID("id");
				if(thisMemory.contains("count", Tag.TAG_INT))
				{
					int count = thisMemory.getInt("count");
					for(int z = 0; z < count; z++)
					{
						eventTimes.add(0l);
					}
				}
				if(thisMemory.contains("times", Tag.TAG_LONG_ARRAY))
				{
					for(long time : thisMemory.getLongArray("times"))
					{
						eventTimes.add(time);
					}
				}
				this.memory.put(id, eventTimes);
			}
		}
		if(compound.contains("ForgetTime", Tag.TAG_LONG))
			this.timeLimit = compound.getLong("ForgetTime");
	}
	
	@Override
	public void handleUpdateMessage(CompoundTag updateInfo)
	{
		if(updateInfo.contains("Limit"))
		{
			this.limit = updateInfo.getInt("Limit");
		}
		else if(updateInfo.contains("TimeLimit"))
		{
			this.timeLimit = updateInfo.getLong("TimeLimit");
		}
		else if(updateInfo.contains("ClearMemory"))
		{
			this.resetMemory();
		}
	}
	
	@Override
	public CompoundTag savePersistentData() {
		CompoundTag data = new CompoundTag();
		ListTag memoryList = new ListTag();
		this.memory.forEach((id, eventTimes) ->{
			CompoundTag thisMemory = new CompoundTag();
			thisMemory.putUUID("id", id);
			thisMemory.putLongArray("times", eventTimes);
			memoryList.add(thisMemory);
		});
		data.put("Memory", memoryList);
		return data;
	}
	
	@Override
	public void loadPersistentData(CompoundTag data) {
		if(data.contains("Memory", Tag.TAG_LIST))
		{
			this.memory.clear();
			ListTag memoryList = data.getList("Memory", Tag.TAG_COMPOUND);
			for(int i = 0; i < memoryList.size(); i++)
			{
				CompoundTag thisMemory = memoryList.getCompound(i);
				UUID id = null;
				List<Long> eventTimes = new ArrayList<>();
				if(thisMemory.contains("id"))
					id = thisMemory.getUUID("id");
				if(thisMemory.contains("count", Tag.TAG_INT))
				{
					int count = thisMemory.getInt("count");
					for(int z = 0; z < count; z++)
					{
						eventTimes.add(0l);
					}
				}
				if(thisMemory.contains("times", Tag.TAG_LONG_ARRAY))
				{
					for(long time : thisMemory.getLongArray("times"))
					{
						eventTimes.add(time);
					}
				}
				this.memory.put(id, eventTimes);
			}
		}
	}
	
	@Override
	public void loadFromJson(JsonObject json) {
		if(json.has("Limit"))
			this.limit = json.get("Limit").getAsInt();
		if(json.has("ForgetTime"))
			this.timeLimit = json.get("ForgetTime").getAsLong();
	}
	
	@Override
	public IconData getButtonIcon() { return IconAndButtonUtil.ICON_COUNT_PLAYER; }

	@Override
	@OnlyIn(Dist.CLIENT)
	public TradeRule.GUIHandler createHandler(TradeRuleScreen screen, Supplier<TradeRule> rule)
	{
		return new GUIHandler(screen, rule);
	}
	
	@OnlyIn(Dist.CLIENT)
	private static class GUIHandler extends TradeRule.GUIHandler
	{
		
		private final PlayerTradeLimit getRule()
		{
			if(getRuleRaw() instanceof PlayerTradeLimit)
				return (PlayerTradeLimit)getRuleRaw();
			return null;
		}
		
		GUIHandler(TradeRuleScreen screen, Supplier<TradeRule> rule)
		{
			super(screen, rule);
		}
		
		EditBox limitInput;
		Button buttonSetLimit;
		Button buttonClearMemory;
		
		TimeInputWidget timeInput;
		
		@Override
		public void initTab() {
			
			this.limitInput = this.addCustomRenderable(new EditBox(screen.getFont(), screen.guiLeft() + 10, screen.guiTop() + 19, 30, 20, Component.empty()));
			this.limitInput.setMaxLength(3);
			this.limitInput.setValue(Integer.toString(this.getRule().limit));
			
			this.buttonSetLimit = this.addCustomRenderable(new Button(screen.guiLeft() + 41, screen.guiTop() + 19, 40, 20, Component.translatable("gui.button.lightmanscurrency.playerlimit.setlimit"), this::PressSetLimitButton));
			this.buttonClearMemory = this.addCustomRenderable(new Button(screen.guiLeft() + 10, screen.guiTop() + 50, screen.xSize - 20, 20, Component.translatable("gui.button.lightmanscurrency.playerlimit.clearmemory"), this::PressClearMemoryButton));
			
			this.timeInput = this.addCustomRenderable(new TimeInputWidget(screen.guiLeft() + 48, screen.guiTop() + 87, 10, TimeUnit.DAY, TimeUnit.MINUTE, this::addCustomRenderable, this::onTimeSet));
			this.timeInput.setTime(this.getRule().timeLimit);
			
		}

		@Override
		public void renderTab(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
			
			screen.getFont().draw(pose, Component.translatable("gui.button.lightmanscurrency.playerlimit.info", this.getRule().limit).getString(), screen.guiLeft() + 10, screen.guiTop() + 9, 0xFFFFFF);
			
			Component text = this.getRule().timeLimit > 0 ? Component.translatable("gui.widget.lightmanscurrency.playerlimit.duration", new TimeData(this.getRule().timeLimit).getShortString()) : Component.translatable("gui.widget.lightmanscurrency.playerlimit.noduration");
			TextRenderUtil.drawCenteredText(pose, text, this.screen.guiLeft() + this.screen.xSize / 2, this.screen.guiTop() + 75, 0xFFFFFF);
			
			if(this.buttonClearMemory.isMouseOver(mouseX, mouseY))
				screen.renderTooltip(pose, Component.translatable("gui.button.lightmanscurrency.playerlimit.clearmemory.tooltip"), mouseX, mouseY);
			
		}

		@Override
		public void onTabClose() {
			
			this.removeCustomWidget(this.limitInput);
			this.removeCustomWidget(this.buttonSetLimit);
			this.removeCustomWidget(this.buttonClearMemory);
			
			this.timeInput.removeChildren(this::removeCustomWidget);
			this.removeCustomWidget(this.timeInput);
			
		}
		
		@Override
		public void onScreenTick() {
			
			TextInputUtil.whitelistInteger(this.limitInput, 1, 100);
			
		}
		
		void PressSetLimitButton(Button button)
		{
			int limit = MathUtil.clamp(TextInputUtil.getIntegerValue(this.limitInput), 1, 100);
			this.getRule().limit = limit;
			CompoundTag updateInfo = new CompoundTag();
			updateInfo.putInt("Limit", limit);
			this.screen.sendUpdateMessage(this.getRuleRaw(), updateInfo);
		}
		
		void PressClearMemoryButton(Button button)
		{
			this.getRule().memory.clear();
			CompoundTag updateInfo = new CompoundTag();
			updateInfo.putBoolean("ClearMemory", true);
			this.screen.sendUpdateMessage(this.getRuleRaw(), updateInfo);
		}

		public void onTimeSet(TimeData newTime) {
			long timeLimit = MathUtil.clamp(newTime.miliseconds, 0, Long.MAX_VALUE);
			this.getRule().timeLimit = timeLimit;
			CompoundTag updateInfo = new CompoundTag();
			updateInfo.putLong("TimeLimit", timeLimit);
			this.screen.sendUpdateMessage(this.getRuleRaw(), updateInfo);
		}
		
	}
	
}
