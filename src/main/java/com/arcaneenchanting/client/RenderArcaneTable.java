package com.arcaneenchanting.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBook;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.arcaneenchanting.ArcaneEnchanting;
import com.arcaneenchanting.Config;
import com.arcaneenchanting.block.BlockArcaneTable;
import com.arcaneenchanting.tile.TableAnimationState;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Renders the Arcane Enchanting Table.
 *
 * <p>
 * Render pass 0 draws the solid parts (pedestal, corner crystals, book, floating item). The glow pass (pass 1 by
 * default, see {@link Config#glowRenderPass}) draws every translucent effect with depth writes disabled: rune
 * inlays, the ground magic circle, rune rings, bookshelf threads, halos, the ritual links, the light pillar and the
 * shockwave.
 */
@SideOnly(Side.CLIENT)
public class RenderArcaneTable extends TileEntitySpecialRenderer {

    static final ResourceLocation TEX_BOOK = new ResourceLocation("textures/entity/enchanting_table_book.png");
    private static final ResourceLocation TEX_CIRCLE = fx("magic_circle");
    private static final ResourceLocation TEX_RUNES = fx("rune_band");
    private static final ResourceLocation TEX_GLOW = fx("glow");
    private static final ResourceLocation TEX_BEAM = fx("beam");
    private static final ResourceLocation TEX_THREAD = fx("thread");
    private static final ResourceLocation TEX_SHOCK = fx("shockwave");

    private static final int RING_SEGMENTS = 48;
    private static final int THREAD_SEGMENTS = 12;

    private final ModelBook bookModel = new ModelBook();
    private EntityItem displayItem;

    private static ResourceLocation fx(String name) {
        return new ResourceLocation(ArcaneEnchanting.MODID, "textures/fx/" + name + ".png");
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileArcaneTable)) {
            return;
        }
        TileArcaneTable tile = (TileArcaneTable) te;
        int pass = MinecraftForgeClient.getRenderPass();
        boolean solid = pass != 1;
        boolean glow = pass == -1 || pass == Config.glowRenderPass;
        if (solid) {
            renderSolid(tile, x, y, z, partial);
        }
        if (glow) {
            renderGlow(tile, x, y, z, partial);
        }
    }

    // ================================================================== solid pass

    private void renderSolid(TileArcaneTable t, double x, double y, double z, float partial) {
        TableAnimationState a = t.anim;
        float time = a.ticks + partial;
        float awaken = TableAnimationState.lerp(a.awakenPrev, a.awaken, partial);
        float charge = TableAnimationState.lerp(a.chargePrev, a.charge, partial);

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        bindTexture(TextureMap.locationBlocksTexture);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        drawPedestal(tess);
        tess.draw();

        drawCrystalsFullBright(time, charge);
        renderBook(a, partial, awaken, charge);

        ItemStack stack = t.getRitualItem();
        if (t.isRitualActive() && stack != null) {
            renderFloatingItem(t, stack, partial, time);
        }
        GL11.glPopMatrix();
    }

    /** Pedestal geometry; the block-atlas texture must be bound and the tessellator drawing quads. */
    static void drawPedestal(Tessellator t) {
        IIcon side = BlockArcaneTable.iconSide, top = BlockArcaneTable.iconTop, stone = BlockArcaneTable.iconStone;
        if (side == null || top == null || stone == null) {
            return;
        }
        // plinth
        box(t, 0.0, 0.0, 0.0, 1.0, 0.1875, 1.0, side, top, stone, 0.0);
        // column (top and bottom are hidden)
        box(t, 0.1875, 0.1875, 0.1875, 0.8125, 0.5625, 0.8125, side, null, null, 0.0);
        // cap
        box(t, 0.0625, 0.5625, 0.0625, 0.9375, 0.75, 0.9375, side, top, stone, 0.0);
        // corner posts
        for (float[] c : TableLayout.CORNERS) {
            box(t, c[0] - 0.0625, 0.75, c[1] - 0.0625, c[0] + 0.0625, 0.875, c[1] + 0.0625, side, top, null, 0.0);
        }
    }

    /** Emissive rune inlays drawn over the pedestal (block atlas, glow pass). */
    private static void drawRuneOverlay(Tessellator t) {
        IIcon side = BlockArcaneTable.iconSideRunes, top = BlockArcaneTable.iconTopRunes;
        if (side == null || top == null) {
            return;
        }
        double e = 0.003;
        box(t, 0.1875, 0.1875, 0.1875, 0.8125, 0.5625, 0.8125, side, null, null, e);
        box(t, 0.0625, 0.5625, 0.0625, 0.9375, 0.75, 0.9375, side, top, null, e);
    }

    /**
     * Axis-aligned box with pixel-consistent UV mapping (u from the horizontal coordinate, v from the height).
     * Null icons skip the corresponding faces. {@code grow} inflates the box (used for overlays).
     */
    private static void box(Tessellator t, double x0, double y0, double z0, double x1, double y1, double z1,
        IIcon side, IIcon top, IIcon bottom, double grow) {
        double gx0 = x0 - grow, gy0 = y0 - grow, gz0 = z0 - grow;
        double gx1 = x1 + grow, gy1 = y1 + grow, gz1 = z1 + grow;
        if (top != null) {
            t.setNormal(0.0F, 1.0F, 0.0F);
            t.addVertexWithUV(gx0, gy1, gz0, u(top, x0), v(top, z0));
            t.addVertexWithUV(gx0, gy1, gz1, u(top, x0), v(top, z1));
            t.addVertexWithUV(gx1, gy1, gz1, u(top, x1), v(top, z1));
            t.addVertexWithUV(gx1, gy1, gz0, u(top, x1), v(top, z0));
        }
        if (bottom != null) {
            t.setNormal(0.0F, -1.0F, 0.0F);
            t.addVertexWithUV(gx0, gy0, gz0, u(bottom, x0), v(bottom, z0));
            t.addVertexWithUV(gx1, gy0, gz0, u(bottom, x1), v(bottom, z0));
            t.addVertexWithUV(gx1, gy0, gz1, u(bottom, x1), v(bottom, z1));
            t.addVertexWithUV(gx0, gy0, gz1, u(bottom, x0), v(bottom, z1));
        }
        if (side != null) {
            double vt = v(side, 1.0 - y1), vb = v(side, 1.0 - y0);
            // north (-Z)
            t.setNormal(0.0F, 0.0F, -1.0F);
            t.addVertexWithUV(gx0, gy0, gz0, u(side, 1.0 - x0), vb);
            t.addVertexWithUV(gx0, gy1, gz0, u(side, 1.0 - x0), vt);
            t.addVertexWithUV(gx1, gy1, gz0, u(side, 1.0 - x1), vt);
            t.addVertexWithUV(gx1, gy0, gz0, u(side, 1.0 - x1), vb);
            // south (+Z)
            t.setNormal(0.0F, 0.0F, 1.0F);
            t.addVertexWithUV(gx0, gy0, gz1, u(side, x0), vb);
            t.addVertexWithUV(gx1, gy0, gz1, u(side, x1), vb);
            t.addVertexWithUV(gx1, gy1, gz1, u(side, x1), vt);
            t.addVertexWithUV(gx0, gy1, gz1, u(side, x0), vt);
            // west (-X)
            t.setNormal(-1.0F, 0.0F, 0.0F);
            t.addVertexWithUV(gx0, gy0, gz0, u(side, z0), vb);
            t.addVertexWithUV(gx0, gy0, gz1, u(side, z1), vb);
            t.addVertexWithUV(gx0, gy1, gz1, u(side, z1), vt);
            t.addVertexWithUV(gx0, gy1, gz0, u(side, z0), vt);
            // east (+X)
            t.setNormal(1.0F, 0.0F, 0.0F);
            t.addVertexWithUV(gx1, gy0, gz0, u(side, 1.0 - z0), vb);
            t.addVertexWithUV(gx1, gy1, gz0, u(side, 1.0 - z0), vt);
            t.addVertexWithUV(gx1, gy1, gz1, u(side, 1.0 - z1), vt);
            t.addVertexWithUV(gx1, gy0, gz1, u(side, 1.0 - z1), vb);
        }
    }

    private static double u(IIcon icon, double f) {
        return icon.getInterpolatedU(f * 16.0D);
    }

    private static double v(IIcon icon, double f) {
        return icon.getInterpolatedV(f * 16.0D);
    }

    /** Corner crystals, full bright and unlit. Leaves the block atlas bound. */
    static void drawCrystalsFullBright(float time, float charge) {
        IIcon icon = BlockArcaneTable.iconCrystal;
        if (icon == null) {
            return;
        }
        float bx = OpenGlHelper.lastBrightnessX, by = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        for (int i = 0; i < 4; ++i) {
            float[] c = TableLayout.CORNERS[i];
            double cy = TableLayout.crystalY(i, time, charge);
            float spin = time * (2.0F + 10.0F * charge) + i * 45.0F;
            octahedron(t, c[0], cy, c[1], 0.075, 0.13, spin, icon);
        }
        t.draw();

        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bx, by);
    }

    /** Octahedron drawn as degenerate quads (the fourth vertex repeats the third). */
    private static void octahedron(Tessellator t, double cx, double cy, double cz, double rh, double rv,
        float spinDeg, IIcon icon) {
        double[] ex = new double[4], ez = new double[4];
        for (int k = 0; k < 4; ++k) {
            double ang = Math.toRadians(spinDeg + 90.0 * k);
            ex[k] = cx + Math.cos(ang) * rh;
            ez[k] = cz + Math.sin(ang) * rh;
        }
        double u0 = icon.getMinU(), u1 = icon.getMaxU(), um = (u0 + u1) * 0.5;
        double v0 = icon.getMinV(), v1 = icon.getMaxV();
        for (int k = 0; k < 4; ++k) {
            int n = (k + 1) & 3;
            float shadeTop = (k & 1) == 0 ? 1.0F : 0.82F;
            float shadeBottom = (k & 1) == 0 ? 0.62F : 0.5F;
            t.setColorRGBA_F(0.86F * shadeTop, 0.72F * shadeTop, 1.0F * shadeTop, 1.0F);
            t.addVertexWithUV(cx, cy + rv, cz, um, v0);
            t.addVertexWithUV(ex[n], cy, ez[n], u1, v1);
            t.addVertexWithUV(ex[k], cy, ez[k], u0, v1);
            t.addVertexWithUV(ex[k], cy, ez[k], u0, v1);
            t.setColorRGBA_F(0.7F * shadeBottom, 0.55F * shadeBottom, 1.0F * shadeBottom, 1.0F);
            t.addVertexWithUV(cx, cy - rv, cz, um, v0);
            t.addVertexWithUV(ex[k], cy, ez[k], u0, v1);
            t.addVertexWithUV(ex[n], cy, ez[n], u1, v1);
            t.addVertexWithUV(ex[n], cy, ez[n], u1, v1);
        }
    }

    private void renderBook(TableAnimationState a, float partial, float awaken, float charge) {
        float time = a.ticks + partial;
        GL11.glPushMatrix();
        GL11.glTranslatef(0.5F, TableLayout.bookY(awaken, charge) + MathHelper.sin(time * 0.1F) * 0.02F, 0.5F);

        float delta = TableClientTicker.wrapRadians(a.bookRotation - a.bookRotationPrev);
        float rotation = a.bookRotationPrev + delta * partial;
        GL11.glRotatef(-rotation * 180.0F / (float) Math.PI, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(80.0F, 0.0F, 0.0F, 1.0F);
        bindTexture(TEX_BOOK);

        float flip = TableAnimationState.lerp(a.pageFlipPrev, a.pageFlip, partial);
        float page1 = pagePos(flip + 0.25F);
        float page2 = pagePos(flip + 0.75F);
        float spread = TableAnimationState.lerp(a.bookSpreadPrev, a.bookSpread, partial);

        GL11.glEnable(GL11.GL_CULL_FACE);
        bookModel.render(null, time, page1, page2, spread, 0.0F, 0.0625F);
        GL11.glPopMatrix();
    }

    private static float pagePos(float f) {
        f = (f - (float) Math.floor(f)) * 1.6F - 0.3F;
        return f < 0.0F ? 0.0F : (f > 1.0F ? 1.0F : f);
    }

    private void renderFloatingItem(TileArcaneTable t, ItemStack stack, float partial, float time) {
        TableAnimationState a = t.anim;
        if (displayItem == null || displayItem.worldObj != t.getWorldObj()) {
            displayItem = new EntityItem(t.getWorldObj(), 0.0D, 0.0D, 0.0D, stack.copy());
        }
        displayItem.setEntityItemStack(stack);
        displayItem.hoverStart = 0.0F;

        float rise = TableAnimationState.lerp(a.itemRisePrev, a.itemRise, partial);
        float spin = TableAnimationState.lerp(a.itemSpinPrev, a.itemSpin, partial);
        float bob = MathHelper.sin(time * 0.12F) * 0.05F;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.5F, TableLayout.itemY(rise) + bob - 0.15F, 0.5F);
        GL11.glRotatef(spin, 0.0F, 1.0F, 0.0F);
        GL11.glScalef(1.6F, 1.6F, 1.6F);
        float bx = OpenGlHelper.lastBrightnessX, by = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        RenderItem.renderInFrame = true;
        RenderManager.instance.renderEntityWithPosYaw(displayItem, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
        RenderItem.renderInFrame = false;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bx, by);
        GL11.glPopMatrix();
    }

    // ================================================================== glow pass

    private void renderGlow(TileArcaneTable t, double x, double y, double z, float partial) {
        TableAnimationState a = t.anim;
        float time = a.ticks + partial;
        float awaken = TableAnimationState.lerp(a.awakenPrev, a.awaken, partial);
        float charge = TableAnimationState.lerp(a.chargePrev, a.charge, partial);
        float flash = TableAnimationState.lerp(a.flashPrev, a.flash, partial);
        float rise = TableAnimationState.lerp(a.itemRisePrev, a.itemRise, partial);
        boolean ritual = t.isRitualActive();

        double bookY = TableLayout.bookY(awaken, charge) + 0.1;
        double itemY = TableLayout.itemY(rise);
        // Camera position in block-local coordinates.
        double camX = -x, camY = -y, camZ = -z;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        float bx = OpenGlHelper.lastBrightnessX, by = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        if (Config.additiveGlow) {
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
        } else {
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        }
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        Tessellator tess = Tessellator.instance;

        // 1. Rune inlays on the pedestal.
        float pulse = 0.8F + 0.2F * MathHelper.sin(time * 0.12F);
        float inlay = Math.min(1.0F, (0.2F + 0.6F * awaken) * pulse + 0.6F * flash + 0.3F * charge);
        bindTexture(TextureMap.locationBlocksTexture);
        tess.startDrawingQuads();
        tess.setColorRGBA_F(0.78F, 0.55F, 1.0F, inlay);
        drawRuneOverlay(tess);
        tess.draw();

        // 2. Ground magic circle.
        if (Config.groundCircle) {
            bindTexture(TEX_CIRCLE);
            float r = 2.2F + 0.15F * MathHelper.sin(time * 0.03F) + 0.6F * charge;
            tess.startDrawingQuads();
            flatQuad(
                tess,
                0.5,
                0.02,
                0.5,
                r,
                time * 0.25F,
                0.70F,
                0.45F,
                1.0F,
                clampAlpha(0.55F * awaken + 0.6F * flash));
            flatQuad(
                tess,
                0.5,
                0.025,
                0.5,
                r * 0.55F,
                -time * 0.6F,
                1.0F,
                0.8F,
                0.45F,
                clampAlpha(0.35F * awaken + 0.5F * flash));
            tess.draw();
        }

        // 3. Rune rings.
        bindTexture(TEX_RUNES);
        for (int k = 0; k < TableAnimationState.RINGS; ++k) {
            float alpha = TableAnimationState.lerp(a.ringAlphaPrev[k], a.ringAlpha[k], partial);
            if (alpha < 0.01F) {
                continue;
            }
            float angle = TableAnimationState.lerp(a.ringAnglePrev[k], a.ringAngle[k], partial);
            float radius = TableLayout.RING_RADIUS[k] * (1.0F - 0.6F * charge);
            float height = TableLayout.RING_HEIGHT[k] + ((float) itemY - TableLayout.RING_HEIGHT[k]) * 0.85F * charge;
            float[] col = TableLayout.RING_COLOR[k];
            float band = TableLayout.RING_BAND[k];

            GL11.glPushMatrix();
            GL11.glTranslatef(0.5F, height, 0.5F);
            GL11.glRotatef(time * 0.3F * k, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(TableLayout.RING_TILT[k], 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(angle, 0.0F, 1.0F, 0.0F);
            tess.startDrawingQuads();
            tess.setColorRGBA_F(col[0], col[1], col[2], clampAlpha(alpha * 0.85F + 0.5F * flash));
            ringBand(tess, radius, band, TableLayout.RING_REPEAT[k]);
            tess.setColorRGBA_F(col[0], col[1], col[2], clampAlpha(alpha * 0.22F));
            ringBand(tess, radius * 1.02F, band * 1.9F, TableLayout.RING_REPEAT[k]);
            tess.draw();
            GL11.glPopMatrix();
        }

        // 4. Light threads from the bookshelves to the book.
        if (Config.shelfThreads && awaken > 0.02F && !a.shelves.isEmpty()) {
            bindTexture(TEX_THREAD);
            double width = 0.035 + 0.02 * charge;
            double[] pts = new double[(THREAD_SEGMENTS + 1) * 3];
            tess.startDrawingQuads();
            int idx = 0;
            for (int[] s : a.shelves) {
                double len = Math.sqrt(s[0] * s[0] + s[2] * s[2]);
                double px = s[0] + 0.5 - s[0] / len * 0.45;
                double py = s[1] + 0.5;
                double pz = s[2] + 0.5 - s[2] / len * 0.45;
                double mx = (px + 0.5) * 0.5, my = (py + bookY) * 0.5 + 0.7, mz = (pz + 0.5) * 0.5;
                for (int i = 0; i <= THREAD_SEGMENTS; ++i) {
                    double f = (double) i / THREAD_SEGMENTS, g = 1.0 - f;
                    pts[i * 3] = g * g * px + 2 * g * f * mx + f * f * 0.5;
                    pts[i * 3 + 1] = g * g * py + 2 * g * f * my + f * f * bookY;
                    pts[i * 3 + 2] = g * g * pz + 2 * g * f * mz + f * f * 0.5;
                }
                float alpha = 0.45F * awaken * (0.7F + 0.3F * MathHelper.sin(time * 0.13F + idx * 1.7F))
                    + 0.4F * charge;
                tess.setColorRGBA_F(0.62F, 0.55F, 1.0F, clampAlpha(alpha));
                ribbon(tess, pts, THREAD_SEGMENTS + 1, width, camX, camY, camZ, -time * 0.04, 2.0 / THREAD_SEGMENTS);
                idx++;
            }
            tess.draw();
        }

        // 5. Halos.
        bindTexture(TEX_GLOW);
        tess.startDrawingQuads();
        billboard(tess, 0.5, bookY, 0.5, 0.55 + 0.35 * awaken, 0.7F, 0.5F, 1.0F, 0.35F * awaken + 0.3F * charge);
        for (int i = 0; i < 4; ++i) {
            float[] c = TableLayout.CORNERS[i];
            billboard(
                tess,
                c[0],
                TableLayout.crystalY(i, time, charge),
                c[1],
                0.22 + 0.1 * charge,
                0.75F,
                0.6F,
                1.0F,
                0.25F + 0.45F * awaken);
        }
        if (ritual) {
            billboard(tess, 0.5, itemY, 0.5, 0.5 + 1.0 * charge, 1.0F, 0.85F, 0.55F, 0.3F + 0.6F * charge);
        }
        if (flash > 0.01F) {
            billboard(tess, 0.5, ritual ? itemY : 1.6, 0.5, 1.0 + 4.0 * flash, 1.0F, 0.92F, 0.8F, flash);
        }
        tess.draw();

        // 6. Ritual links: crystals and table to the floating item.
        if (ritual && charge > 0.02F) {
            bindTexture(TEX_BEAM);
            double[] pts = new double[6];
            tess.startDrawingQuads();
            tess.setColorRGBA_F(0.8F, 0.65F, 1.0F, clampAlpha(charge * 0.8F));
            for (int i = 0; i < 4; ++i) {
                float[] c = TableLayout.CORNERS[i];
                pts[0] = c[0];
                pts[1] = TableLayout.crystalY(i, time, charge);
                pts[2] = c[1];
                pts[3] = 0.5;
                pts[4] = itemY;
                pts[5] = 0.5;
                ribbon(tess, pts, 2, 0.03 + 0.05 * charge, camX, camY, camZ, -time * 0.08, 1.0);
            }
            tess.setColorRGBA_F(1.0F, 0.85F, 0.55F, clampAlpha(charge * 0.6F));
            pts[0] = 0.5;
            pts[1] = TableLayout.TOP;
            pts[2] = 0.5;
            ribbon(tess, pts, 2, 0.08 * charge, camX, camY, camZ, -time * 0.08, 1.0);
            tess.draw();
        }

        // 7. Climax: light pillar and shockwaves.
        if (a.climaxAge >= 0) {
            float age = a.climaxAge + partial;
            float grow = Math.min(1.0F, age / 4.0F);
            float fade = Math.max(0.0F, 1.0F - age / 40.0F);
            if (fade > 0.0F) {
                double[] pts = { 0.5, TableLayout.TOP, 0.5, 0.5, TableLayout.TOP + 14.0 * grow, 0.5 };
                bindTexture(TEX_BEAM);
                tess.startDrawingQuads();
                tess.setColorRGBA_F(0.85F, 0.7F, 1.0F, clampAlpha(fade * 0.6F));
                ribbon(tess, pts, 2, 0.15 + 0.6 * fade, camX, camY, camZ, -time * 0.1, 7.0 * grow);
                tess.setColorRGBA_F(1.0F, 0.97F, 0.9F, clampAlpha(fade));
                ribbon(tess, pts, 2, 0.06 + 0.24 * fade, camX, camY, camZ, -time * 0.15, 7.0 * grow);
                tess.draw();
            }
            bindTexture(TEX_SHOCK);
            tess.startDrawingQuads();
            shockwave(tess, age, 0.0F, 1.0F);
            shockwave(tess, age, 6.0F, 0.5F);
            tess.draw();
        }

        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(true);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, bx, by);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }

    private static void shockwave(Tessellator t, float age, float delay, float strength) {
        float f = age - delay;
        if (f < 0.0F || f > 30.0F) {
            return;
        }
        float alpha = (1.0F - f / 30.0F) * strength;
        flatQuad(t, 0.5, 0.04 + delay * 0.002, 0.5, 0.6F + f * 0.38F, 0.0F, 0.9F, 0.75F, 1.0F, clampAlpha(alpha));
    }

    // ================================================================== geometry helpers

    private static float clampAlpha(float a) {
        return a < 0.0F ? 0.0F : (a > 1.0F ? 1.0F : a);
    }

    /** Horizontal textured square centred on (cx, y, cz), rotated around the vertical axis. */
    private static void flatQuad(Tessellator t, double cx, double y, double cz, float halfSize, float rotDeg, float r,
        float g, float b, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        double rad = Math.toRadians(rotDeg);
        double c = Math.cos(rad) * halfSize, s = Math.sin(rad) * halfSize;
        t.setColorRGBA_F(r, g, b, alpha);
        t.addVertexWithUV(cx + (-c + s), y, cz + (-s - c), 0.0, 0.0);
        t.addVertexWithUV(cx + (-c - s), y, cz + (-s + c), 0.0, 1.0);
        t.addVertexWithUV(cx + (c - s), y, cz + (s + c), 1.0, 1.0);
        t.addVertexWithUV(cx + (c + s), y, cz + (s - c), 1.0, 0.0);
    }

    /** Vertical cylindrical band of glyphs around the origin. */
    private static void ringBand(Tessellator t, float radius, float band, int repeat) {
        double h = band * 0.5;
        for (int s = 0; s < RING_SEGMENTS; ++s) {
            double a0 = 2.0 * Math.PI * s / RING_SEGMENTS, a1 = 2.0 * Math.PI * (s + 1) / RING_SEGMENTS;
            double x0 = Math.cos(a0) * radius, z0 = Math.sin(a0) * radius;
            double x1 = Math.cos(a1) * radius, z1 = Math.sin(a1) * radius;
            double u0 = (double) s / RING_SEGMENTS * repeat, u1 = (double) (s + 1) / RING_SEGMENTS * repeat;
            t.addVertexWithUV(x0, -h, z0, u0, 1.0);
            t.addVertexWithUV(x1, -h, z1, u1, 1.0);
            t.addVertexWithUV(x1, h, z1, u1, 0.0);
            t.addVertexWithUV(x0, h, z0, u0, 0.0);
        }
    }

    /** Camera-facing sprite (same construction as vanilla particles). */
    private static void billboard(Tessellator t, double px, double py, double pz, double size, float r, float g,
        float b, float alpha) {
        if (alpha <= 0.005F) {
            return;
        }
        double rx = ActiveRenderInfo.rotationX * size, rz = ActiveRenderInfo.rotationZ * size;
        double ryz = ActiveRenderInfo.rotationYZ * size, rxy = ActiveRenderInfo.rotationXY * size;
        double rxz = ActiveRenderInfo.rotationXZ * size;
        t.setColorRGBA_F(r, g, b, clampAlpha(alpha));
        t.addVertexWithUV(px - rx - ryz, py - rxz, pz - rz - rxy, 1.0, 1.0);
        t.addVertexWithUV(px - rx + ryz, py + rxz, pz - rz + rxy, 1.0, 0.0);
        t.addVertexWithUV(px + rx + ryz, py + rxz, pz + rz + rxy, 0.0, 0.0);
        t.addVertexWithUV(px + rx - ryz, py - rxz, pz + rz - rxy, 0.0, 1.0);
    }

    /**
     * Camera-facing strip along a polyline. u runs across the strip (0..1); v runs along it, starting at
     * {@code vStart} and advancing by {@code vStep} per point.
     */
    private static void ribbon(Tessellator t, double[] pts, int count, double width, double camX, double camY,
        double camZ, double vStart, double vStep) {
        double[] side = new double[count * 3];
        for (int i = 0; i < count; ++i) {
            int a = Math.max(0, i - 1), b = Math.min(count - 1, i + 1);
            double tx = pts[b * 3] - pts[a * 3], ty = pts[b * 3 + 1] - pts[a * 3 + 1], tz = pts[b * 3 + 2]
                - pts[a * 3 + 2];
            double vx = camX - pts[i * 3], vy = camY - pts[i * 3 + 1], vz = camZ - pts[i * 3 + 2];
            double sx = ty * vz - tz * vy, sy = tz * vx - tx * vz, sz = tx * vy - ty * vx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1.0E-6) {
                sx = 1.0;
                sy = 0.0;
                sz = 0.0;
                len = 1.0;
            }
            side[i * 3] = sx / len * width;
            side[i * 3 + 1] = sy / len * width;
            side[i * 3 + 2] = sz / len * width;
        }
        for (int i = 0; i + 1 < count; ++i) {
            int j = i + 1;
            double v0 = vStart + vStep * i, v1 = vStart + vStep * j;
            t.addVertexWithUV(
                pts[i * 3] - side[i * 3],
                pts[i * 3 + 1] - side[i * 3 + 1],
                pts[i * 3 + 2] - side[i * 3 + 2],
                0.0,
                v0);
            t.addVertexWithUV(
                pts[i * 3] + side[i * 3],
                pts[i * 3 + 1] + side[i * 3 + 1],
                pts[i * 3 + 2] + side[i * 3 + 2],
                1.0,
                v0);
            t.addVertexWithUV(
                pts[j * 3] + side[j * 3],
                pts[j * 3 + 1] + side[j * 3 + 1],
                pts[j * 3 + 2] + side[j * 3 + 2],
                1.0,
                v1);
            t.addVertexWithUV(
                pts[j * 3] - side[j * 3],
                pts[j * 3 + 1] - side[j * 3 + 1],
                pts[j * 3 + 2] - side[j * 3 + 2],
                0.0,
                v1);
        }
    }

    // ================================================================== item rendering

    /** Static model used by the item renderer (pedestal, crystals and closed book), in block space 0..1. */
    void renderStaticModel() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        drawPedestal(tess);
        tess.draw();
        drawCrystalsFullBright(0.0F, 0.0F);

        GL11.glPushMatrix();
        GL11.glTranslatef(0.5F, TableLayout.bookY(0.0F, 0.0F), 0.5F);
        GL11.glRotatef(80.0F, 0.0F, 0.0F, 1.0F);
        mc.getTextureManager().bindTexture(TEX_BOOK);
        bookModel.render(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F);
        GL11.glPopMatrix();
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    }
}
