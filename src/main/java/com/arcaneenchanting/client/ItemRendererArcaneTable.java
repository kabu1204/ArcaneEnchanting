package com.arcaneenchanting.client;

import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Renders the table item (inventory, held, dropped) with the static part of the table model. */
@SideOnly(Side.CLIENT)
public class ItemRendererArcaneTable implements IItemRenderer {

    private final RenderArcaneTable model;

    public ItemRendererArcaneTable(RenderArcaneTable model) {
        this.model = model;
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        switch (type) {
            case ENTITY:
                return helper == ItemRendererHelper.ENTITY_ROTATION || helper == ItemRendererHelper.ENTITY_BOBBING
                    || helper == ItemRendererHelper.BLOCK_3D;
            case INVENTORY:
                return helper == ItemRendererHelper.INVENTORY_BLOCK;
            case EQUIPPED:
            case EQUIPPED_FIRST_PERSON:
                return helper == ItemRendererHelper.EQUIPPED_BLOCK;
            default:
                return false;
        }
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        GL11.glPushMatrix();
        switch (type) {
            case ENTITY:
                // Forge scales custom 3D items to 0.25; restore the usual dropped-block size.
                GL11.glScalef(2.0F, 2.0F, 2.0F);
                GL11.glTranslatef(-0.5F, -0.4F, -0.5F);
                break;
            case INVENTORY:
                GL11.glTranslatef(-0.5F, -0.55F, -0.5F);
                break;
            default:
                // EQUIPPED_BLOCK already translated the model into block space.
                break;
        }
        model.renderStaticModel();
        GL11.glPopMatrix();
    }
}
