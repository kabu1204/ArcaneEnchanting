package com.arcaneenchanting.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnchantmentNameParts;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.arcaneenchanting.ArcaneEnchanting;
import com.arcaneenchanting.inventory.ContainerArcaneTable;
import com.arcaneenchanting.inventory.EnchantOptions;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Screen of the Arcane Enchanting Table. Drawn entirely in code (no GUI texture) apart from the rotating magic
 * circle behind the item slot.
 */
@SideOnly(Side.CLIENT)
public class GuiArcaneTable extends GuiContainer {

    private static final ResourceLocation TEX_CIRCLE = new ResourceLocation(
        ArcaneEnchanting.MODID,
        "textures/fx/magic_circle.png");

    private static final int OPT_X = 78, OPT_Y = 16, OPT_W = 92, OPT_H = 24, OPT_STEP = 26;
    private static final int RESCRY_Y = 96, RESCRY_H = 14;
    /** Glyph lines that fit inside one option (two 9-pixel lines in a 24-pixel option). */
    private static final int GLYPH_LINES = 2;
    private static final int CIRCLE_X = ContainerArcaneTable.SLOT_X + 8, CIRCLE_Y = ContainerArcaneTable.SLOT_Y + 8;

    private static final int C_GOLD = 0xFFC8963C, C_GOLD_LIGHT = 0xFFF0D080, C_EDGE = 0xFF3A2858;

    private final ContainerArcaneTable container;

    public GuiArcaneTable(ContainerArcaneTable container) {
        super(container);
        this.container = container;
        this.xSize = 176;
        this.ySize = 222;
    }

    // ------------------------------------------------------------------ state helpers

    private boolean hasItem() {
        return container.tableInventory.getStackInSlot(0) != null;
    }

    private boolean isBusy() {
        TileArcaneTable tile = container.getTile();
        return tile != null && tile.isRitualActive();
    }

    private boolean canAfford(int levels) {
        return mc.thePlayer.capabilities.isCreativeMode || mc.thePlayer.experienceLevel >= levels;
    }

    private boolean isOptionShown(int i) {
        return hasItem() && container.data[ContainerArcaneTable.D_LEVEL + i] > 0
            && container.data[ContainerArcaneTable.D_CLUE_ID + i] >= 0;
    }

    private boolean canSelect(int i) {
        return isOptionShown(i) && !isBusy() && canAfford(container.data[ContainerArcaneTable.D_LEVEL + i]);
    }

    private boolean canRescry() {
        if (!hasItem() || isBusy() || !canAfford(container.getRescryCost())) {
            return false;
        }
        for (int i = 0; i < EnchantOptions.COUNT; ++i) {
            if (isOptionShown(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        int rx = mx - guiLeft, ry = my - guiTop;
        return rx >= x && rx < x + w && ry >= y && ry < y + h;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        int l = guiLeft, t = guiTop;

        // Panel and frame.
        drawGradientRect(l, t, l + xSize, t + ySize, 0xF61A0E2E, 0xF60A0616);
        frame(l, t, xSize, ySize, C_GOLD);
        frame(l + 2, t + 2, xSize - 4, ySize - 4, C_EDGE);
        for (int[] c : new int[][] { { l, t }, { l + xSize - 4, t }, { l, t + ySize - 4 },
            { l + xSize - 4, t + ySize - 4 } }) {
            drawRect(c[0], c[1], c[0] + 4, c[1] + 4, C_GOLD_LIGHT);
        }
        drawRect(l + 7, t + ContainerArcaneTable.INV_Y - 14, l + xSize - 7, t + ContainerArcaneTable.INV_Y - 13, C_EDGE);

        // Rotating magic circle behind the item slot.
        float time = (mc.theWorld != null ? mc.theWorld.getTotalWorldTime() : 0L) + partial;
        drawCircle(l + CIRCLE_X, t + CIRCLE_Y, 36.0F, time * 0.8F, 0.70F, 0.45F, 1.0F, 0.85F);
        drawCircle(l + CIRCLE_X, t + CIRCLE_Y, 21.0F, -time * 1.6F, 1.0F, 0.80F, 0.45F, 0.55F);

        // Slots.
        for (Object o : inventorySlots.inventorySlots) {
            Slot s = (Slot) o;
            int sx = l + s.xDisplayPosition - 1, sy = t + s.yDisplayPosition - 1;
            boolean table = s.slotNumber == 0;
            drawRect(sx, sy, sx + 18, sy + 18, table ? C_GOLD : 0xFF120A1E);
            drawRect(sx + 1, sy + 1, sx + 18, sy + 18, table ? 0xFF6A4A20 : 0xFF3C2C5A);
            drawRect(sx + 1, sy + 1, sx + 17, sy + 17, table ? 0xE0140A22 : 0xFF1C1230);
        }

        // Enchanting options.
        EnchantmentNameParts.instance.reseedRandomGenerator(container.data[ContainerArcaneTable.D_NAME_SEED]);
        for (int i = 0; i < EnchantOptions.COUNT; ++i) {
            int x = l + OPT_X, y = t + OPT_Y + i * OPT_STEP;
            String glyphs = EnchantmentNameParts.instance.generateNewRandomName();
            if (!isOptionShown(i)) {
                drawRect(x, y, x + OPT_W, y + OPT_H, 0x50100C18);
                frame(x, y, OPT_W, OPT_H, 0xFF241A36);
                continue;
            }
            int level = container.data[ContainerArcaneTable.D_LEVEL + i];
            boolean selectable = canSelect(i);
            boolean hover = selectable && inRect(mx, my, OPT_X, OPT_Y + i * OPT_STEP, OPT_W, OPT_H);
            drawRect(x, y, x + OPT_W, y + OPT_H, hover ? 0xE0503478 : (selectable ? 0xC0302048 : 0x90201A28));
            frame(x, y, OPT_W, OPT_H, hover ? C_GOLD_LIGHT : (selectable ? C_GOLD : 0xFF3A3046));

            int glyphColor = hover ? 0xFFE9A8 : (selectable ? 0xB89CFF : 0x5E5070);
            drawGlyphs(glyphs, x + 4, y + 3, OPT_W - 24, glyphColor);
            String lv = String.valueOf(level);
            int lvColor = selectable ? 0x80FF20 : 0x407020;
            fontRendererObj.drawStringWithShadow(
                lv,
                x + OPT_W - 4 - fontRendererObj.getStringWidth(lv),
                y + OPT_H - 10,
                lvColor);
        }

        // Re-scry button.
        int bx = l + OPT_X, by = t + RESCRY_Y;
        boolean rescry = canRescry();
        boolean rescryHover = rescry && inRect(mx, my, OPT_X, RESCRY_Y, OPT_W, RESCRY_H);
        drawRect(bx, by, bx + OPT_W, by + RESCRY_H, rescryHover ? 0xE0284870 : (rescry ? 0xC0182C48 : 0x60101018));
        frame(bx, by, OPT_W, RESCRY_H, rescryHover ? 0xFF9AD8FF : (rescry ? 0xFF4A78A8 : 0xFF2A2A3A));
        drawCenteredString(
            fontRendererObj,
            I18n.format("gui.arcaneenchanting.rescry", container.getRescryCost()),
            bx + OPT_W / 2,
            by + 3,
            rescry ? 0xD8F0FF : 0x606878);

        // Enchanting power.
        int power = Math.min(container.data[ContainerArcaneTable.D_POWER], 15);
        drawCenteredString(
            fontRendererObj,
            I18n.format("gui.arcaneenchanting.power", power),
            l + CIRCLE_X,
            t + RESCRY_Y + 3,
            0xC8B4FF);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Draws the decorative glyph name, wrapped to the given width and limited to the lines that fit an option. */
    private void drawGlyphs(String glyphs, int x, int y, int width, int color) {
        FontRenderer font = mc.standardGalacticFontRenderer;
        List<?> lines = font.listFormattedStringToWidth(glyphs, width);
        int max = Math.min(lines.size(), GLYPH_LINES);
        for (int i = 0; i < max; ++i) {
            font.drawString(String.valueOf(lines.get(i)), x, y + i * font.FONT_HEIGHT, color);
        }
    }

    private void frame(int x, int y, int w, int h, int color) {
        drawRect(x, y, x + w, y + 1, color);
        drawRect(x, y + h - 1, x + w, y + h, color);
        drawRect(x, y + 1, x + 1, y + h - 1, color);
        drawRect(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private void drawCircle(float cx, float cy, float radius, float rotDeg, float r, float g, float b, float a) {
        mc.getTextureManager().bindTexture(TEX_CIRCLE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0F);
        GL11.glRotatef(rotDeg, 0.0F, 0.0F, 1.0F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(r, g, b, a);
        t.addVertexWithUV(-radius, radius, zLevel, 0.0D, 1.0D);
        t.addVertexWithUV(radius, radius, zLevel, 1.0D, 1.0D);
        t.addVertexWithUV(radius, -radius, zLevel, 1.0D, 0.0D);
        t.addVertexWithUV(-radius, -radius, zLevel, 0.0D, 0.0D);
        t.draw();
        GL11.glPopMatrix();
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString(I18n.format("container.arcaneenchanting.table"), 8, 5, 0xE6C878);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ContainerArcaneTable.INV_Y - 11, 0xB8A6D8);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        List<String> tip = new ArrayList<String>();

        for (int i = 0; i < EnchantOptions.COUNT; ++i) {
            if (!isOptionShown(i) || !inRect(mx, my, OPT_X, OPT_Y + i * OPT_STEP, OPT_W, OPT_H)) {
                continue;
            }
            int id = container.data[ContainerArcaneTable.D_CLUE_ID + i];
            int lvl = container.data[ContainerArcaneTable.D_CLUE_LEVEL + i];
            int hidden = container.data[ContainerArcaneTable.D_HIDDEN + i];
            int cost = container.data[ContainerArcaneTable.D_LEVEL + i];
            Enchantment e = id >= 0 && id < Enchantment.enchantmentsList.length ? Enchantment.enchantmentsList[id]
                : null;
            if (e != null) {
                tip.add("§d" + e.getTranslatedName(lvl));
            }
            if (hidden > 0) {
                tip.add("§7" + I18n.format("gui.arcaneenchanting.hidden", hidden));
            }
            tip.add(
                canAfford(cost) ? "§a" + I18n.format("gui.arcaneenchanting.cost", cost)
                    : "§c" + I18n.format("gui.arcaneenchanting.requires", cost));
            if (isBusy()) {
                tip.add("§6" + I18n.format("gui.arcaneenchanting.busy"));
            }
        }
        if (inRect(mx, my, OPT_X, RESCRY_Y, OPT_W, RESCRY_H)) {
            tip.add("§b" + I18n.format("gui.arcaneenchanting.rescry.tip"));
            tip.add("§7" + I18n.format("gui.arcaneenchanting.rescry.cost", container.getRescryCost()));
        }
        if (!hasItem() && inRect(mx, my, OPT_X, OPT_Y, OPT_W, OPT_STEP * 2 + OPT_H)) {
            tip.add("§7" + I18n.format("gui.arcaneenchanting.empty"));
        }
        if (!tip.isEmpty()) {
            drawHoveringText(tip, mx, my, fontRendererObj);
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        super.mouseClicked(mx, my, button);
        if (button != 0) {
            return;
        }
        for (int i = 0; i < EnchantOptions.COUNT; ++i) {
            if (inRect(mx, my, OPT_X, OPT_Y + i * OPT_STEP, OPT_W, OPT_H) && canSelect(i)) {
                mc.playerController.sendEnchantPacket(inventorySlots.windowId, i);
                // Close the screen so that the player watches the ritual.
                mc.thePlayer.closeScreen();
                return;
            }
        }
        if (inRect(mx, my, OPT_X, RESCRY_Y, OPT_W, RESCRY_H) && canRescry()) {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, ContainerArcaneTable.ACTION_RESCRY);
            mc.getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
        }
    }
}
