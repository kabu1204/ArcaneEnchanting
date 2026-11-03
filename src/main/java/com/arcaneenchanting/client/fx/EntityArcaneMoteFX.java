package com.arcaneenchanting.client.fx;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Full-bright glowing particle with two motion modes:
 * <ul>
 * <li>drift: free motion with drag (ambient motes, bursts);</li>
 * <li>converge: travels from a start point to a target point along an accelerating spiral (energy gathering).</li>
 * </ul>
 * The texture comes from the block atlas (FX layer 1), so the particle batches with other terrain particles.
 */
@SideOnly(Side.CLIENT)
public class EntityArcaneMoteFX extends EntityFX {

    private final boolean converge;
    private final double startX, startY, startZ, targetX, targetY, targetZ;
    private final float drag;
    private final float spiralTurns;
    private final float spiralPhase;
    private final float baseScale;
    private final float baseAlpha;

    private EntityArcaneMoteFX(World world, double x, double y, double z, double vx, double vy, double vz,
        boolean converge, double tx, double ty, double tz, float r, float g, float b, float scale, int life,
        float drag, float spiralTurns, IIcon icon) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.motionX = vx;
        this.motionY = vy;
        this.motionZ = vz;
        this.converge = converge;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        this.drag = drag;
        this.spiralTurns = spiralTurns;
        this.spiralPhase = rand.nextFloat() * 6.2832F;
        this.baseScale = scale;
        this.baseAlpha = 1.0F;
        this.particleMaxAge = Math.max(2, life);
        this.particleScale = scale;
        this.particleGravity = 0.0F;
        this.noClip = true;
        setRBGColorF(r, g, b);
        setAlphaF(0.0F);
        setParticleIcon(icon);
    }

    /** Free-moving mote. */
    public static EntityArcaneMoteFX drift(World world, double x, double y, double z, double vx, double vy,
        double vz, float r, float g, float b, float scale, int life, float drag, IIcon icon) {
        return new EntityArcaneMoteFX(world, x, y, z, vx, vy, vz, false, x, y, z, r, g, b, scale, life, drag, 0.0F,
            icon);
    }

    /** Mote that spirals from (x, y, z) into (tx, ty, tz). */
    public static EntityArcaneMoteFX converge(World world, double x, double y, double z, double tx, double ty,
        double tz, float r, float g, float b, float scale, int life, float spiralTurns, IIcon icon) {
        return new EntityArcaneMoteFX(world, x, y, z, 0.0D, 0.0D, 0.0D, true, tx, ty, tz, r, g, b, scale, life, 1.0F,
            spiralTurns, icon);
    }

    @Override
    public int getFXLayer() {
        return 1;
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (particleAge++ >= particleMaxAge) {
            setDead();
            return;
        }
        float t = (float) particleAge / (float) particleMaxAge;

        if (converge) {
            float e = t * t;
            double bx = startX + (targetX - startX) * e;
            double by = startY + (targetY - startY) * e;
            double bz = startZ + (targetZ - startZ) * e;
            float radius = (1.0F - e) * 0.35F;
            float angle = spiralPhase + spiralTurns * 6.2832F * e;
            posX = bx + MathHelper.cos(angle) * radius;
            posY = by;
            posZ = bz + MathHelper.sin(angle) * radius;
        } else {
            posX += motionX;
            posY += motionY;
            posZ += motionZ;
            motionX *= drag;
            motionY *= drag;
            motionZ *= drag;
        }

        float envelope = MathHelper.sin(t * 3.1416F);
        particleAlpha = baseAlpha * envelope;
        particleScale = baseScale * (0.55F + 0.45F * envelope);
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        return 0xF000F0;
    }

    @Override
    public float getBrightness(float partialTicks) {
        return 1.0F;
    }
}
