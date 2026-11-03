package com.arcaneenchanting.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import com.arcaneenchanting.ArcaneEnchanting;
import com.arcaneenchanting.inventory.GuiHandler;
import com.arcaneenchanting.tile.TileArcaneTable;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockArcaneTable extends BlockContainer {

    // Icons from the block atlas, used by the renderer and the particles.
    @SideOnly(Side.CLIENT)
    public static IIcon iconSide, iconTop, iconStone, iconSideRunes, iconTopRunes, iconCrystal, iconMote, iconSpark;

    public BlockArcaneTable() {
        super(Material.rock);
        setBlockName(ArcaneEnchanting.MODID + ".arcane_table");
        setBlockTextureName(ArcaneEnchanting.MODID + ":arcane_stone");
        setHardness(5.0F);
        setResistance(2000.0F);
        setStepSound(soundTypeStone);
        setLightLevel(0.5F);
        setLightOpacity(0);
        setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.75F, 1.0F);
        setCreativeTab(CreativeTabs.tabDecorations);
        setHarvestLevel("pickaxe", 0);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileArcaneTable();
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    /** Everything is drawn by the tile entity renderer. */
    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileArcaneTable) || ((TileArcaneTable) te).isRitualActive()) {
            return true;
        }
        player.openGui(ArcaneEnchanting.instance, GuiHandler.GUI_ARCANE_TABLE, world, x, y, z);
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileArcaneTable) {
            ((TileArcaneTable) te).dropRitualItem();
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        String p = ArcaneEnchanting.MODID + ":";
        blockIcon = reg.registerIcon(p + "arcane_stone");
        iconStone = blockIcon;
        iconSide = reg.registerIcon(p + "arcane_side");
        iconTop = reg.registerIcon(p + "arcane_top");
        iconSideRunes = reg.registerIcon(p + "arcane_side_runes");
        iconTopRunes = reg.registerIcon(p + "arcane_top_runes");
        iconCrystal = reg.registerIcon(p + "arcane_crystal");
        iconMote = reg.registerIcon(p + "arcane_mote");
        iconSpark = reg.registerIcon(p + "arcane_spark");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return side == 1 ? (iconTop != null ? iconTop : blockIcon) : (iconSide != null ? iconSide : blockIcon);
    }
}
