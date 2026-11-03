package com.arcaneenchanting;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** Mod configuration, stored in config/arcaneenchanting.cfg. */
public final class Config {

    private static final String CAT_GAMEPLAY = "gameplay";
    private static final String CAT_VISUAL = "visual";
    private static final String CAT_AUDIO = "audio";

    // gameplay
    public static int rescryCost = 1;
    public static int ritualTicks = 60;
    public static int ritualTicksPerTier = 12;

    // visual
    public static float particleMultiplier = 1.0F;
    public static boolean additiveGlow = true;
    public static boolean groundCircle = true;
    public static boolean shelfThreads = true;
    public static int glowRenderPass = 1;

    // audio
    public static boolean ambientHum = true;
    public static float soundVolume = 1.0F;

    private Config() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        cfg.load();

        rescryCost = cfg.getInt(
            "rescryCost",
            CAT_GAMEPLAY,
            rescryCost,
            0,
            30,
            "Experience levels consumed by the Re-scry button, which rerolls the three offered enchantments.");
        ritualTicks = cfg.getInt(
            "ritualTicks",
            CAT_GAMEPLAY,
            ritualTicks,
            30,
            200,
            "Base duration of the enchanting ritual in ticks (20 ticks = 1 second).");
        ritualTicksPerTier = cfg.getInt(
            "ritualTicksPerTier",
            CAT_GAMEPLAY,
            ritualTicksPerTier,
            0,
            60,
            "Extra ritual ticks added for the second and third enchanting option.");

        particleMultiplier = cfg.getFloat(
            "particleMultiplier",
            CAT_VISUAL,
            particleMultiplier,
            0.0F,
            4.0F,
            "Scales the number of particles spawned by the table (0 disables custom particles).");
        additiveGlow = cfg.getBoolean(
            "additiveGlow",
            CAT_VISUAL,
            additiveGlow,
            "Use additive blending for glowing effects. Set to false if glow effects appear as dark or "
                + "opaque shapes when a shader pack is active.");
        groundCircle = cfg.getBoolean(
            "groundCircle",
            CAT_VISUAL,
            groundCircle,
            "Draw the rotating magic circle on the ground around the table.");
        shelfThreads = cfg.getBoolean(
            "shelfThreads",
            CAT_VISUAL,
            shelfThreads,
            "Draw light threads between the table and each bookshelf that contributes power.");
        glowRenderPass = cfg.getInt(
            "glowRenderPass",
            CAT_VISUAL,
            glowRenderPass,
            0,
            1,
            "Render pass used for translucent glow effects. 1 (default) draws them after translucent terrain. "
                + "Set to 0 if glow effects are missing when a shader pack is active.");

        ambientHum = cfg.getBoolean(
            "ambientHum",
            CAT_AUDIO,
            ambientHum,
            "Play a quiet ambient hum while a player stands near an awakened table.");
        soundVolume = cfg.getFloat(
            "soundVolume",
            CAT_AUDIO,
            soundVolume,
            0.0F,
            2.0F,
            "Volume multiplier for all sounds played by the table.");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }
}
