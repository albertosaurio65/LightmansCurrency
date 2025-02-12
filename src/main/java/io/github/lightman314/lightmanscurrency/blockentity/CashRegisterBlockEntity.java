package io.github.lightman314.lightmanscurrency.blockentity;

import java.util.ArrayList;
import java.util.List;

import io.github.lightman314.lightmanscurrency.LightmansCurrency;
import io.github.lightman314.lightmanscurrency.common.traders.ITraderSource;
import io.github.lightman314.lightmanscurrency.common.traders.TraderData;
import io.github.lightman314.lightmanscurrency.core.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkHooks;

public class CashRegisterBlockEntity extends BlockEntity implements ITraderSource{
	
	List<BlockPos> positions = new ArrayList<>();
	
	public CashRegisterBlockEntity(BlockPos pos, BlockState state)
	{
		super(ModBlockEntities.CASH_REGISTER.get(), pos, state);
	}
	
	public void loadDataFromItems(CompoundTag itemTag)
	{
		if(itemTag == null)
			return;
		readPositions(itemTag);
	}
	
	public void OpenContainer(Player player)
	{
		MenuProvider provider = TraderData.getTraderMenuProvider(this.worldPosition);
		if(!(player instanceof ServerPlayer))
		{
			LightmansCurrency.LogError("Player is not a server player entity. Cannot open the trade menu.");
			return;
		}
		if(this.getTraders().size() > 0)
			NetworkHooks.openScreen((ServerPlayer)player, provider, this.worldPosition);
		else
			player.sendSystemMessage(Component.translatable("message.lightmanscurrency.cash_register.notlinked"));
	}
	
	@Override
	public boolean isSingleTrader() { return false; }
	
	@Override
	public List<TraderData> getTraders() { 
		List<TraderData> traders = new ArrayList<>();
		for(int i = 0; i < this.positions.size(); ++i)
		{
			BlockEntity be = this.level.getBlockEntity(this.positions.get(i));
			if(be instanceof TraderBlockEntity<?>)
			{
				TraderData trader = ((TraderBlockEntity<?>)be).getTraderData();
				if(trader != null)
					traders.add(trader);
			}
		}
		return traders;
	}
	
	@Override
	public void saveAdditional(CompoundTag compound)
	{
		
		ListTag storageList = new ListTag();
		for(int i = 0; i < positions.size(); i++)
		{
			CompoundTag thisEntry = new CompoundTag();
			BlockPos thisPos = positions.get(i);
			thisEntry.putInt("x", thisPos.getX());
			thisEntry.putInt("y", thisPos.getY());
			thisEntry.putInt("z", thisPos.getZ());
			storageList.add(thisEntry);
		}
		
		if(storageList.size() > 0)
		{
			compound.put("TraderPos", storageList);
		}
		
		super.saveAdditional(compound);
	}
	
	@Override
	public void load(CompoundTag compound)
	{
		
		readPositions(compound);
		
		super.load(compound);
		
	}
	
	private void readPositions(CompoundTag compound)
	{
		if(compound.contains("TraderPos"))
		{
			this.positions = new ArrayList<>();
			ListTag storageList = compound.getList("TraderPos", Tag.TAG_COMPOUND);
			for(int i = 0; i < storageList.size(); i++)
			{
				CompoundTag thisEntry = storageList.getCompound(i);
				if(thisEntry.contains("x") && thisEntry.contains("y") && thisEntry.contains("z"))
				{
					BlockPos thisPos = new BlockPos(thisEntry.getInt("x"), thisEntry.getInt("y"), thisEntry.getInt("z"));
					this.positions.add(thisPos);
				}
			}
		}
	}
	
	@Override
	public CompoundTag getUpdateTag() { return this.saveWithoutMetadata(); }
	
}
