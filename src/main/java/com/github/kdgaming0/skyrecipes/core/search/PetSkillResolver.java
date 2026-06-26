package com.github.kdgaming0.skyrecipes.core.search;

import com.github.kdgaming0.skyrecipes.core.model.NeuItem;
import com.github.kdgaming0.skyrecipes.core.util.TextUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Resolves the SkyBlock skill a pet belongs to (mining, combat, farming, ...) from
 * its lore subtitle.
 *
 * <p>Every pet's first lore line is a {@code §8<Skill> Pet} subtitle — also
 * {@code Mount} for rideable pets (Rock, Armadillo) and {@code Morph} (Rat). This
 * matches NEU's authoritative {@code pets.json} {@code pet_types} for every pet, so
 * the already-loaded subtitle is used directly and no extra constant data is needed.</p>
 *
 * <p>The Bingo pet's {@code All Skills} subtitle names no single skill and resolves to
 * {@code null}, matching the in-game pet menu which lists it under no skill tab.</p>
 */
public final class PetSkillResolver {

    /** Canonical SkyBlock skills a pet can belong to. */
    public static final Set<String> PET_SKILLS = Set.of(
            "mining", "combat", "farming", "fishing",
            "foraging", "alchemy", "enchanting", "taming"
    );

    /** Subtitle nouns that follow the skill word, e.g. "Mining <Pet|Mount|Morph>". */
    private static final Set<String> SUBTITLE_NOUNS = Set.of("pet", "mount", "morph");

    private PetSkillResolver() {
    }

    /**
     * Returns the pet's skill, or {@code null} if {@code item} has no recognized
     * {@code <Skill> Pet/Mount/Morph} subtitle (non-pets and Bingo included).
     */
    @Nullable
    public static String resolve(NeuItem item) {
        if (item.lore() == null || item.lore().isEmpty()) {
            return null;
        }
        String subtitle = TextUtil.stripColorCodes(item.lore().getFirst()).trim().toLowerCase();
        int space = subtitle.indexOf(' ');
        if (space <= 0) {
            return null;
        }
        String skill = subtitle.substring(0, space);
        String noun = subtitle.substring(space + 1).trim();
        if (PET_SKILLS.contains(skill) && SUBTITLE_NOUNS.contains(noun)) {
            return skill;
        }
        return null;
    }
}
