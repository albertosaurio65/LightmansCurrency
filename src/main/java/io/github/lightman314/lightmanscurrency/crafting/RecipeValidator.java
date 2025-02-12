package io.github.lightman314.lightmanscurrency.crafting;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;


public class RecipeValidator {
	
	public static Results getValidRecipes(Level level)
	{
		Results results = new Results();
		RecipeManager recipeManager = level.getRecipeManager();
		for(Recipe<?> recipe : getRecipes(recipeManager, RecipeTypes.COIN_MINT.get()))
		{
			if(recipe instanceof CoinMintRecipe)
			{
				CoinMintRecipe mintRecipe = (CoinMintRecipe)recipe;
				if(mintRecipe.isValid())
				{
					results.coinMintRecipes.add(mintRecipe);
				}
			}
		}
		return results;
	}
	
	private static Collection<Recipe<?>> getRecipes(RecipeManager recipeManager, RecipeType<?> recipeType)
	{
		return recipeManager.getRecipes().stream().filter(recipe -> recipe.getType() == recipeType).collect(Collectors.toSet());
	}
	
	public static class Results
	{
		private final List<CoinMintRecipe> coinMintRecipes = Lists.newArrayList();
		
		
		public List<CoinMintRecipe> getCoinMintRecipes() { return this.coinMintRecipes; }
	}
	
}
