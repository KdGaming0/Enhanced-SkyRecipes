package com.github.kdgaming0.skyrecipes;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyRecipes implements ClientModInitializer {
    public static final String MOD_ID = "skyrecipes";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.1.1";

    @Override
    public void onInitializeClient() {

    }
}