package com.arcaneenchanting.util;

import java.util.List;

import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

/**
 * Bookshelf scanning shared by the server (enchanting power) and the client (visual threads).
 *
 * <p>
 * The rules mirror the Forge-patched vanilla enchanting table: a block two steps away counts when the two
 * blocks between it and the table (at the table level and one above) are air. Modded bookshelves are supported
 * through {@link ForgeHooks#getEnchantPower}.
 */
public final class ArcaneGeometry {

    /** Vanilla caps the enchanting power at 15 bookshelves. */
    public static final int MAX_POWER = 15;

    private ArcaneGeometry() {}

    /**
     * Scans the bookshelf ring around a table.
     *
     * @param out if not null, receives {dx, dy, dz} offsets of every block that contributes power
     * @return the summed enchanting power (not yet capped)
     */
    public static float scan(World world, int x, int y, int z, List<int[]> out) {
        float power = 0.0F;
        for (int dz = -1; dz <= 1; ++dz) {
            for (int dx = -1; dx <= 1; ++dx) {
                if ((dx == 0 && dz == 0) || !world.isAirBlock(x + dx, y, z + dz)
                    || !world.isAirBlock(x + dx, y + 1, z + dz)) {
                    continue;
                }
                power += probe(world, x, y, z, dx * 2, 0, dz * 2, out);
                power += probe(world, x, y, z, dx * 2, 1, dz * 2, out);
                if (dx != 0 && dz != 0) {
                    power += probe(world, x, y, z, dx * 2, 0, dz, out);
                    power += probe(world, x, y, z, dx * 2, 1, dz, out);
                    power += probe(world, x, y, z, dx, 0, dz * 2, out);
                    power += probe(world, x, y, z, dx, 1, dz * 2, out);
                }
            }
        }
        return power;
    }

    private static float probe(World world, int x, int y, int z, int dx, int dy, int dz, List<int[]> out) {
        float p = ForgeHooks.getEnchantPower(world, x + dx, y + dy, z + dz);
        if (p > 0.0F && out != null) {
            out.add(new int[] { dx, dy, dz });
        }
        return p;
    }
}
