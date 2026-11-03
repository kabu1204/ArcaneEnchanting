package com.arcaneenchanting.client;

import net.minecraft.util.MathHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Geometry shared by the renderer and the particle logic (block-local coordinates, origin at the block corner). */
@SideOnly(Side.CLIENT)
public final class TableLayout {

    /** Height of the table top surface. */
    public static final float TOP = 0.75F;

    /** Corner crystal positions (x, z). */
    public static final float[][] CORNERS = { { 0.125F, 0.125F }, { 0.875F, 0.125F }, { 0.875F, 0.875F },
        { 0.125F, 0.875F } };

    // Rune rings: radius, height, band height, tilt (degrees), glyph repetitions, base speed (degrees per tick).
    public static final float[] RING_RADIUS = { 0.92F, 1.18F, 1.42F };
    public static final float[] RING_HEIGHT = { 1.05F, 1.32F, 0.82F };
    public static final float[] RING_BAND = { 0.20F, 0.17F, 0.15F };
    public static final float[] RING_TILT = { 0.0F, 9.0F, -13.0F };
    public static final int[] RING_REPEAT = { 3, 4, 5 };
    public static final float[] RING_SPEED = { 1.1F, -0.75F, 0.5F };
    public static final float[][] RING_COLOR = { { 0.70F, 0.48F, 1.00F }, { 0.45F, 0.75F, 1.00F },
        { 1.00F, 0.80F, 0.45F } };

    private TableLayout() {}

    public static float easeOut(float t) {
        float u = 1.0F - Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - u * u * u;
    }

    public static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    /** Height of the floating book. */
    public static float bookY(float awaken, float charge) {
        return TOP + 0.12F + 0.30F * awaken + 0.10F * charge;
    }

    /** Height of the item floating above the table during the ritual. */
    public static float itemY(float rise) {
        return TOP + 0.55F + 1.05F * easeOut(rise);
    }

    /** Height of a corner crystal. */
    public static float crystalY(int index, float time, float charge) {
        return 0.875F + 0.16F + 0.04F * MathHelper.sin(time * 0.08F + index * 1.5708F) + 0.25F * charge;
    }
}
