package com.arcaneenchanting.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.arcaneenchanting.Config;
import com.arcaneenchanting.block.BlockArcaneTable;
import com.arcaneenchanting.client.fx.EntityArcaneMoteFX;
import com.arcaneenchanting.tile.TableAnimationState;
import com.arcaneenchanting.tile.TileArcaneTable;
import com.arcaneenchanting.util.ArcaneGeometry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Advances the client animation of a table and emits its particles and sounds. */
@SideOnly(Side.CLIENT)
public final class TableClientTicker {

    private static final Random RAND = new Random();
    private static final float TWO_PI = 6.2831855F;

    // Particle palette.
    private static final float[][] PALETTE = { { 0.78F, 0.55F, 1.00F }, { 0.55F, 0.80F, 1.00F },
        { 1.00F, 0.86F, 0.55F }, { 1.00F, 1.00F, 1.00F } };

    private TableClientTicker() {}

    // ================================================================== per-tick update

    public static void tick(TileArcaneTable t) {
        World world = t.getWorldObj();
        TableAnimationState a = t.anim;
        a.storePrevious();
        a.ticks++;

        double cx = t.xCoord + 0.5D, cy = t.yCoord + 0.5D, cz = t.zCoord + 0.5D;
        boolean ritual = t.isRitualActive();

        // Bookshelves and power (rescanned once per second).
        if (a.ticks % 20 == 1) {
            List<int[]> found = new ArrayList<int[]>();
            float p = ArcaneGeometry.scan(world, t.xCoord, t.yCoord, t.zCoord, found);
            a.shelves = found;
            a.powerTarget = Math.min(ArcaneGeometry.MAX_POWER, p);
        }
        a.power += (a.powerTarget - a.power) * 0.1F;

        // Awakening.
        EntityPlayer near = world.getClosestPlayer(cx, cy, cz, 7.0D);
        float target = near != null || ritual ? 1.0F : 0.0F;
        a.awaken += (target - a.awaken) * (target > a.awaken ? 0.05F : 0.025F);
        if (a.awaken > 0.3F && !a.awakeSoundPlayed) {
            sound(world, cx, cy, cz, "mob.endermen.portal", 0.35F, 0.5F);
            a.awakeSoundPlayed = true;
        } else if (a.awaken < 0.05F) {
            a.awakeSoundPlayed = false;
        }

        // Ritual progress.
        if (ritual) {
            if (t.isRevealed()) {
                a.charge = Math.max(0.0F, a.charge - 0.08F);
            } else {
                a.charge = TableLayout.clamp01((float) t.getRitualAge() / (float) t.getClimaxTick());
            }
            a.itemRise = Math.min(1.0F, a.itemRise + 0.05F);
        } else {
            a.charge *= 0.85F;
            a.itemRise = 0.0F;
            a.itemRisePrev = 0.0F;
        }
        a.itemSpin += 2.0F + 14.0F * a.charge + 8.0F * a.flash;
        a.flash *= 0.88F;
        if (a.climaxAge >= 0 && ++a.climaxAge > 60) {
            a.climaxAge = -1;
        }

        // Rune rings: the number of visible rings follows the enchanting power.
        float[] ringTarget = { a.awaken * (0.4F + 0.6F * TableLayout.clamp01(a.power / 5.0F)),
            a.awaken * TableLayout.clamp01((a.power - 5.0F) / 5.0F),
            a.awaken * TableLayout.clamp01((a.power - 10.0F) / 5.0F) };
        float speed = (0.25F + 0.75F * a.awaken) * (1.0F + 7.0F * a.charge * a.charge) + 6.0F * a.flash;
        for (int k = 0; k < TableAnimationState.RINGS; ++k) {
            float tgt = ritual ? 1.0F : ringTarget[k];
            a.ringAlpha[k] += (tgt - a.ringAlpha[k]) * 0.06F;
            a.ringAngle[k] += TableLayout.RING_SPEED[k] * speed;
            if (Math.abs(a.ringAngle[k]) > 3600.0F) {
                float shift = Math.signum(a.ringAngle[k]) * 3600.0F;
                a.ringAngle[k] -= shift;
                a.ringAnglePrev[k] -= shift;
            }
        }
        if (Math.abs(a.itemSpin) > 3600.0F) {
            a.itemSpin -= 3600.0F;
            a.itemSpinPrev -= 3600.0F;
        }

        tickBook(t, a, ritual);
        emitAmbient(t, a, ritual);
    }

    /** Vanilla page-flip and rotation behaviour, extended for the ritual. */
    private static void tickBook(TileArcaneTable t, TableAnimationState a, boolean ritual) {
        double cx = t.xCoord + 0.5D, cz = t.zCoord + 0.5D;
        EntityPlayer reader = t.getWorldObj().getClosestPlayer(cx, t.yCoord + 0.5D, cz, 3.0D);

        if (ritual) {
            a.bookSpread += 0.1F;
            a.bookRotationTarget += 0.06F;
            if (a.ticks % 4 == 0) {
                a.flipTarget += 1 + RAND.nextInt(2);
            }
        } else if (reader != null) {
            a.bookRotationTarget = (float) Math.atan2(reader.posZ - cz, reader.posX - cx);
            a.bookSpread += 0.1F;
            if (a.bookSpread < 0.5F || RAND.nextInt(40) == 0) {
                float old = a.flipTarget;
                do {
                    a.flipTarget += RAND.nextInt(4) - RAND.nextInt(4);
                } while (old == a.flipTarget);
            }
        } else {
            a.bookRotationTarget += 0.02F;
            a.bookSpread -= 0.1F;
        }

        a.bookRotation = wrapRadians(a.bookRotation);
        a.bookRotationTarget = wrapRadians(a.bookRotationTarget);
        float delta = wrapRadians(a.bookRotationTarget - a.bookRotation);
        a.bookRotation += delta * 0.4F;
        a.bookSpread = TableLayout.clamp01(a.bookSpread);

        float f = (a.flipTarget - a.pageFlip) * 0.4F;
        f = Math.max(-0.2F, Math.min(0.2F, f));
        a.flipVelocity += (f - a.flipVelocity) * 0.9F;
        a.pageFlip += a.flipVelocity;
    }

    static float wrapRadians(float r) {
        while (r >= (float) Math.PI) {
            r -= TWO_PI;
        }
        while (r < -(float) Math.PI) {
            r += TWO_PI;
        }
        return r;
    }

    // ================================================================== continuous particles and sounds

    private static void emitAmbient(TileArcaneTable t, TableAnimationState a, boolean ritual) {
        World world = t.getWorldObj();
        float mult = particleFactor();
        double cx = t.xCoord + 0.5D, cz = t.zCoord + 0.5D;
        double bookY = t.yCoord + TableLayout.bookY(a.awaken, a.charge) + 0.1D;

        // Glyphs flowing from every bookshelf into the book (vanilla particle).
        if (mult > 0.0F) {
            float rate = 0.05F * a.awaken * mult * (ritual ? 3.0F : 1.0F);
            for (int[] s : a.shelves) {
                if (RAND.nextFloat() < rate) {
                    double sx = t.xCoord + s[0] + RAND.nextFloat();
                    double sy = t.yCoord + s[1] + RAND.nextFloat();
                    double sz = t.zCoord + s[2] + RAND.nextFloat();
                    world.spawnParticle("enchantmenttable", cx, bookY, cz, sx - cx, sy - bookY, sz - cz);
                }
            }
        }

        IIcon mote = BlockArcaneTable.iconMote;
        IIcon spark = BlockArcaneTable.iconSpark;
        if (mote != null && spark != null && mult > 0.0F) {
            // Ambient motes rising from the magic circle.
            int n = count(0.35F * a.awaken * mult);
            for (int i = 0; i < n; ++i) {
                float ang = RAND.nextFloat() * TWO_PI;
                float r = 0.7F + RAND.nextFloat() * 1.6F;
                float[] c = pick(RAND.nextFloat() < 0.5F ? 0 : (RAND.nextFloat() < 0.6F ? 1 : 2));
                double px = cx + MathHelper.cos(ang) * r, pz = cz + MathHelper.sin(ang) * r;
                double tang = 0.006D;
                spawn(
                    EntityArcaneMoteFX.drift(
                        world,
                        px,
                        t.yCoord + 0.05D + RAND.nextFloat() * 0.3D,
                        pz,
                        -MathHelper.sin(ang) * tang,
                        0.008D + RAND.nextFloat() * 0.02D,
                        MathHelper.cos(ang) * tang,
                        c[0],
                        c[1],
                        c[2],
                        0.5F + RAND.nextFloat() * 0.7F,
                        50 + RAND.nextInt(40),
                        0.99F,
                        mote));
            }

            // During the charge, energy is drawn into the floating item from all around.
            if (ritual && !t.isRevealed()) {
                double iy = t.yCoord + TableLayout.itemY(a.itemRise);
                int m = count((2.0F + 6.0F * a.charge) * mult);
                for (int i = 0; i < m; ++i) {
                    double[] p = randomOnSphere(2.5D + RAND.nextDouble() * 2.0D);
                    float[] c = pick(RAND.nextInt(4));
                    spawn(
                        EntityArcaneMoteFX.converge(
                            world,
                            cx + p[0],
                            iy + Math.abs(p[1]) * 0.6D,
                            cz + p[2],
                            cx,
                            iy,
                            cz,
                            c[0],
                            c[1],
                            c[2],
                            0.8F + RAND.nextFloat() * 0.6F,
                            20 + RAND.nextInt(12),
                            0.6F + RAND.nextFloat(),
                            RAND.nextInt(3) == 0 ? spark : mote));
                }
                // Sparks leaping from the corner crystals to the item.
                if (RAND.nextFloat() < 0.6F * mult) {
                    float[] corner = TableLayout.CORNERS[RAND.nextInt(4)];
                    float time = a.ticks;
                    spawn(
                        EntityArcaneMoteFX.converge(
                            world,
                            t.xCoord + corner[0],
                            t.yCoord + TableLayout.crystalY(0, time, a.charge),
                            t.zCoord + corner[1],
                            cx,
                            iy,
                            cz,
                            0.85F,
                            0.7F,
                            1.0F,
                            0.9F,
                            10 + RAND.nextInt(5),
                            0.0F,
                            spark));
                }
            }
        }

        // Rising chimes while the ritual charges.
        if (ritual && !t.isRevealed()) {
            int interval = Math.max(2, 6 - (int) (a.charge * 5.0F));
            if (t.getRitualAge() % interval == 0) {
                sound(world, cx, t.yCoord + 1.0D, cz, "note.harp", 0.5F, 0.5F + 1.5F * a.charge);
            }
        }

        // Quiet ambient hum.
        if (Config.ambientHum && a.awaken > 0.6F && a.ticks % 140 == 0) {
            sound(world, cx, t.yCoord + 0.5D, cz, "portal.portal", 0.10F * a.awaken, 0.5F);
        }
    }

    // ================================================================== one-shot events

    public static void onEvent(TileArcaneTable t, int id, int param) {
        World world = t.getWorldObj();
        TableAnimationState a = t.anim;
        double cx = t.xCoord + 0.5D, cz = t.zCoord + 0.5D;
        float mult = particleFactor();
        IIcon mote = BlockArcaneTable.iconMote;
        IIcon spark = BlockArcaneTable.iconSpark;
        boolean canSpawn = mote != null && spark != null && mult > 0.0F;

        switch (id) {
            case TileArcaneTable.EVT_RITUAL_START: {
                sound(world, cx, t.yCoord + 1.0D, cz, "portal.trigger", 0.3F, 1.7F);
                sound(world, cx, t.yCoord + 1.0D, cz, "mob.endermen.portal", 0.6F, 0.6F);
                if (canSpawn) {
                    double iy = t.yCoord + TableLayout.itemY(1.0F);
                    int n = (int) (36 * mult);
                    for (int i = 0; i < n; ++i) {
                        float ang = TWO_PI * i / Math.max(1, n);
                        spawn(
                            EntityArcaneMoteFX.converge(
                                world,
                                cx + MathHelper.cos(ang) * 2.2D,
                                t.yCoord + 0.05D,
                                cz + MathHelper.sin(ang) * 2.2D,
                                cx,
                                iy,
                                cz,
                                0.78F,
                                0.55F,
                                1.0F,
                                1.0F,
                                26 + RAND.nextInt(10),
                                1.2F,
                                mote));
                    }
                }
                break;
            }
            case TileArcaneTable.EVT_CLIMAX: {
                a.flash = 1.0F;
                a.flashPrev = 1.0F;
                a.climaxAge = 0;
                float tierBoost = 1.0F + 0.35F * param;
                sound(world, cx, t.yCoord + 1.5D, cz, "random.explode", 0.35F, 1.5F);
                sound(world, cx, t.yCoord + 1.5D, cz, "random.levelup", 0.9F, 0.75F);
                sound(world, cx, t.yCoord + 1.5D, cz, "fireworks.twinkle", 0.8F, 1.0F);
                if (canSpawn) {
                    double iy = t.yCoord + TableLayout.itemY(1.0F);
                    int n = (int) (70 * mult * tierBoost);
                    for (int i = 0; i < n; ++i) {
                        double[] d = randomOnSphere(0.15D + RAND.nextDouble() * 0.3D);
                        float[] c = pick(RAND.nextInt(4));
                        spawn(
                            EntityArcaneMoteFX.drift(
                                world,
                                cx,
                                iy,
                                cz,
                                d[0],
                                d[1],
                                d[2],
                                c[0],
                                c[1],
                                c[2],
                                0.9F + RAND.nextFloat() * 0.8F,
                                25 + RAND.nextInt(20),
                                0.9F,
                                RAND.nextInt(3) == 0 ? spark : mote));
                    }
                    int ring = (int) (48 * mult * tierBoost);
                    for (int i = 0; i < ring; ++i) {
                        float ang = TWO_PI * i / Math.max(1, ring);
                        double v = 0.35D + RAND.nextDouble() * 0.15D;
                        spawn(
                            EntityArcaneMoteFX.drift(
                                world,
                                cx,
                                t.yCoord + 0.1D,
                                cz,
                                MathHelper.cos(ang) * v,
                                0.01D,
                                MathHelper.sin(ang) * v,
                                0.85F,
                                0.7F,
                                1.0F,
                                1.2F,
                                22 + RAND.nextInt(8),
                                0.92F,
                                mote));
                    }
                }
                break;
            }
            case TileArcaneTable.EVT_COMPLETE: {
                sound(world, cx, t.yCoord + 1.5D, cz, "random.orb", 0.7F, 0.55F);
                EntityPlayer player = Minecraft.getMinecraft().thePlayer;
                if (canSpawn && player != null
                    && player.getDistanceSq(cx, t.yCoord + 1.0D, cz) < 24.0D * 24.0D) {
                    double iy = t.yCoord + TableLayout.itemY(1.0F);
                    int n = (int) (24 * mult);
                    for (int i = 0; i < n; ++i) {
                        spawn(
                            EntityArcaneMoteFX.converge(
                                world,
                                cx + (RAND.nextDouble() - 0.5D) * 0.3D,
                                iy,
                                cz + (RAND.nextDouble() - 0.5D) * 0.3D,
                                player.posX,
                                player.posY - 0.4D,
                                player.posZ,
                                1.0F,
                                0.86F,
                                0.55F,
                                0.9F,
                                12 + RAND.nextInt(6),
                                0.4F,
                                RAND.nextBoolean() ? spark : mote));
                    }
                }
                break;
            }
            case TileArcaneTable.EVT_RESCRY: {
                sound(world, cx, t.yCoord + 1.0D, cz, "mob.endermen.portal", 0.5F, 1.3F);
                sound(world, cx, t.yCoord + 1.0D, cz, "note.pling", 0.4F, 1.6F);
                a.flash = Math.max(a.flash, 0.35F);
                if (canSpawn) {
                    double by = t.yCoord + TableLayout.bookY(a.awaken, 0.0F) + 0.1D;
                    int n = (int) (30 * mult);
                    for (int i = 0; i < n; ++i) {
                        float ang = TWO_PI * i / Math.max(1, n);
                        spawn(
                            EntityArcaneMoteFX.converge(
                                world,
                                cx + MathHelper.cos(ang) * 1.6D,
                                by + (RAND.nextDouble() - 0.5D) * 0.4D,
                                cz + MathHelper.sin(ang) * 1.6D,
                                cx,
                                by,
                                cz,
                                0.55F,
                                0.8F,
                                1.0F,
                                0.8F,
                                14 + RAND.nextInt(6),
                                0.8F,
                                mote));
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    // ================================================================== helpers

    private static float particleFactor() {
        int setting = Minecraft.getMinecraft().gameSettings.particleSetting;
        float f = setting == 0 ? 1.0F : (setting == 1 ? 0.5F : 0.15F);
        return f * Config.particleMultiplier;
    }

    /** Converts a fractional rate into an integer count with the right expectation. */
    private static int count(float rate) {
        int n = (int) rate;
        if (RAND.nextFloat() < rate - n) {
            n++;
        }
        return n;
    }

    private static float[] pick(int index) {
        return PALETTE[Math.max(0, Math.min(PALETTE.length - 1, index))];
    }

    private static double[] randomOnSphere(double radius) {
        double u = RAND.nextDouble() * 2.0D - 1.0D;
        double phi = RAND.nextDouble() * Math.PI * 2.0D;
        double s = Math.sqrt(1.0D - u * u);
        return new double[] { Math.cos(phi) * s * radius, u * radius, Math.sin(phi) * s * radius };
    }

    private static void spawn(EntityArcaneMoteFX fx) {
        Minecraft.getMinecraft().effectRenderer.addEffect(fx);
    }

    private static void sound(World world, double x, double y, double z, String name, float volume, float pitch) {
        float v = volume * Config.soundVolume;
        if (v > 0.0F) {
            world.playSound(x, y, z, name, v, pitch, false);
        }
    }
}
