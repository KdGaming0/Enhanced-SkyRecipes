package com.github.kdgaming0.skyrecipes.core.data;

import com.github.kdgaming0.skyrecipes.core.registry.ConstantsRegistry;
import com.github.kdgaming0.skyrecipes.core.registry.ItemRegistry;

import java.nio.file.Path;

/**
 * Immutable result of loading or reloading binary data.
 */
public record DataLoadResult(
        ItemRegistry itemRegistry,
        ConstantsRegistry constantsRegistry,
        Path dataPath,
        BinaryMetadata metadata
) {
}
