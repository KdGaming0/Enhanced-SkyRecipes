package com.github.kdgaming0.skyrecipes.core.recipe;

import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * Indexes recipes by result item and ingredient item for fast lookups.
 *
 * <p>Uses NEU internal names as keys.</p>
 */
public final class RecipeIndex {

    private final Map<String, List<Identifier>> resultIndex;
    private final Map<String, List<Identifier>> ingredientIndex;

    public RecipeIndex(Map<String, List<Identifier>> resultIndex,
                       Map<String, List<Identifier>> ingredientIndex) {
        this.resultIndex = Collections.unmodifiableMap(resultIndex);
        this.ingredientIndex = Collections.unmodifiableMap(ingredientIndex);
    }

    public List<Identifier> getRecipesForResult(String internalName) {
        return resultIndex.getOrDefault(internalName, Collections.emptyList());
    }

    public List<Identifier> getRecipesForIngredient(String internalName) {
        return ingredientIndex.getOrDefault(internalName, Collections.emptyList());
    }

    public boolean hasResult(String internalName) {
        return resultIndex.containsKey(internalName);
    }

    public boolean hasIngredient(String internalName) {
        return ingredientIndex.containsKey(internalName);
    }

    public int resultCount() {
        return resultIndex.size();
    }

    public int ingredientCount() {
        return ingredientIndex.size();
    }

    /**
     * Mutable builder for constructing a {@link RecipeIndex}.
     */
    public static final class Builder {
        private final Map<String, List<Identifier>> resultIndex = new HashMap<>();
        private final Map<String, List<Identifier>> ingredientIndex = new HashMap<>();

        public Builder addResult(String internalName, Identifier recipeId) {
            resultIndex.computeIfAbsent(internalName, k -> new ArrayList<>()).add(recipeId);
            return this;
        }

        public Builder addIngredient(String internalName, Identifier recipeId) {
            ingredientIndex.computeIfAbsent(internalName, k -> new ArrayList<>()).add(recipeId);
            return this;
        }

        public RecipeIndex build() {
            return new RecipeIndex(resultIndex, ingredientIndex);
        }
    }
}
