package com.arcaneenchanting.tile;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side animation state of one table. It contains only plain values, so it is safe to construct on a
 * dedicated server (where it simply stays unused). It is advanced by the client tick handler and read by the
 * renderer, which interpolates between the previous and the current value with the partial tick.
 */
public class TableAnimationState {

    public static final int RINGS = 3;

    public int ticks;

    // Vanilla-style book animation.
    public float pageFlip, pageFlipPrev;
    public float flipTarget, flipVelocity;
    public float bookSpread, bookSpreadPrev;
    public float bookRotation, bookRotationPrev, bookRotationTarget;

    /** 0 = dormant, 1 = fully awakened (a player is near). */
    public float awaken, awakenPrev;
    /** Smoothed enchanting power (0..15) and the value it approaches. */
    public float power, powerTarget;
    /** Offsets {dx, dy, dz} of the bookshelves that contribute power. */
    public List<int[]> shelves = new ArrayList<int[]>();

    public final float[] ringAlpha = new float[RINGS];
    public final float[] ringAlphaPrev = new float[RINGS];
    public final float[] ringAngle = new float[RINGS];
    public final float[] ringAnglePrev = new float[RINGS];

    /** Ritual progress towards the climax (0..1). Decays after the ritual. */
    public float charge, chargePrev;
    /** Short flash after the climax (1 -> 0). */
    public float flash, flashPrev;
    /** Ticks since the climax, or -1. Drives the light pillar and the shockwave. */
    public int climaxAge = -1;
    /** Height factor of the floating item (0..1). */
    public float itemRise, itemRisePrev;
    /** Accumulated spin of the floating item in degrees. */
    public float itemSpin, itemSpinPrev;

    /** Whether the awakening sound has been played since the table was last dormant. */
    public boolean awakeSoundPlayed;

    public static float lerp(float prev, float cur, float partial) {
        return prev + (cur - prev) * partial;
    }

    public void storePrevious() {
        pageFlipPrev = pageFlip;
        bookSpreadPrev = bookSpread;
        bookRotationPrev = bookRotation;
        awakenPrev = awaken;
        chargePrev = charge;
        flashPrev = flash;
        itemRisePrev = itemRise;
        itemSpinPrev = itemSpin;
        for (int i = 0; i < RINGS; ++i) {
            ringAlphaPrev[i] = ringAlpha[i];
            ringAnglePrev[i] = ringAngle[i];
        }
    }
}
