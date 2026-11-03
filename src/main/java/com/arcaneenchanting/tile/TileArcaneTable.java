package com.arcaneenchanting.tile;

import java.util.List;
import java.util.UUID;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import com.arcaneenchanting.ArcaneEnchanting;
import com.arcaneenchanting.Config;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Tile entity of the Arcane Enchanting Table.
 *
 * <p>
 * Server side it runs the enchanting ritual: the item floats above the table while the ritual charges, the
 * enchantments are applied at the climax (so the enchantment glint appears at that moment), and the item is handed
 * to the player when the ritual completes. One-shot visual and sound effects are triggered on clients through
 * vanilla block events; the ritual state itself is synchronised with the tile description packet.
 */
public class TileArcaneTable extends TileEntity {

    public static final int EVT_RITUAL_START = 1;
    public static final int EVT_CLIMAX = 2;
    public static final int EVT_COMPLETE = 3;
    public static final int EVT_RESCRY = 4;

    /** Ticks between the climax and the completion of the ritual. */
    public static final int RELEASE_TICKS = 24;

    // Ritual state (synchronised to clients).
    private ItemStack ritualItem;
    private int ritualAge = -1;
    private int ritualDuration;
    private int ritualTier;
    private boolean revealed;

    // Ritual state (server only).
    private NBTTagList pendingEnchants;
    private boolean pendingIsBook;
    private UUID ritualPlayer;

    /** Client-side animation state, unused on the server. */
    public final TableAnimationState anim = new TableAnimationState();

    // ------------------------------------------------------------------ queries

    public boolean isRitualActive() {
        return ritualAge >= 0 && ritualItem != null;
    }

    public ItemStack getRitualItem() {
        return ritualItem;
    }

    public int getRitualAge() {
        return ritualAge;
    }

    public int getRitualDuration() {
        return ritualDuration;
    }

    public int getRitualTier() {
        return ritualTier;
    }

    public int getClimaxTick() {
        return Math.max(1, ritualDuration - RELEASE_TICKS);
    }

    public boolean isRevealed() {
        return revealed;
    }

    // ------------------------------------------------------------------ ritual (server)

    public void beginRitual(ItemStack item, List<EnchantmentData> enchantments, int tier, EntityPlayer player) {
        ritualItem = item.copy();
        ritualItem.stackSize = 1;
        pendingIsBook = item.getItem() == Items.book;
        pendingEnchants = new NBTTagList();
        for (EnchantmentData ed : enchantments) {
            NBTTagCompound e = new NBTTagCompound();
            e.setInteger("id", ed.enchantmentobj.effectId);
            e.setInteger("lvl", ed.enchantmentLevel);
            pendingEnchants.appendTag(e);
        }
        ritualTier = tier;
        ritualDuration = Config.ritualTicks + tier * Config.ritualTicksPerTier;
        ritualAge = 0;
        revealed = false;
        ritualPlayer = player.getUniqueID();
        markDirty();
        sync();
        fireEvent(EVT_RITUAL_START, tier);
    }

    public void onRescry() {
        fireEvent(EVT_RESCRY, 0);
    }

    @Override
    public void updateEntity() {
        if (worldObj.isRemote) {
            if (ritualAge >= 0 && ritualAge < ritualDuration) {
                ritualAge++;
            }
            ArcaneEnchanting.proxy.tickTableClient(this);
            return;
        }
        if (ritualAge < 0) {
            return;
        }
        if (ritualItem == null) {
            resetRitual();
            return;
        }
        ritualAge++;
        if (!revealed && ritualAge >= getClimaxTick()) {
            applyEnchantments();
            revealed = true;
            sync();
            fireEvent(EVT_CLIMAX, ritualTier);
        }
        if (ritualAge >= ritualDuration) {
            deliverItem();
            resetRitual();
            sync();
            fireEvent(EVT_COMPLETE, 0);
        }
        markDirty();
    }

    private void applyEnchantments() {
        if (pendingEnchants == null || ritualItem == null) {
            return;
        }
        if (pendingIsBook) {
            ItemStack book = new ItemStack(Items.enchanted_book);
            for (int i = 0; i < pendingEnchants.tagCount(); ++i) {
                NBTTagCompound e = pendingEnchants.getCompoundTagAt(i);
                Items.enchanted_book.addEnchantment(book, new EnchantmentData(e.getInteger("id"), e.getInteger("lvl")));
            }
            ritualItem = book;
        } else {
            for (int i = 0; i < pendingEnchants.tagCount(); ++i) {
                NBTTagCompound e = pendingEnchants.getCompoundTagAt(i);
                int id = e.getInteger("id");
                if (id >= 0 && id < Enchantment.enchantmentsList.length && Enchantment.enchantmentsList[id] != null) {
                    ritualItem.addEnchantment(Enchantment.enchantmentsList[id], e.getInteger("lvl"));
                }
            }
        }
        pendingEnchants = null;
    }

    private void deliverItem() {
        if (ritualItem == null) {
            return;
        }
        EntityPlayer player = ritualPlayer == null ? null : worldObj.func_152378_a(ritualPlayer);
        if (player != null && player.worldObj == worldObj
            && player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) < 24.0D * 24.0D
            && player.inventory.addItemStackToInventory(ritualItem)) {
            worldObj.playSoundAtEntity(player, "random.pop", 0.4F, 1.4F);
            ritualItem = null;
            return;
        }
        dropRitualItemAt(xCoord + 0.5D, yCoord + 1.2D, zCoord + 0.5D);
    }

    /** Drops the ritual item (used when the table is broken during a ritual). */
    public void dropRitualItem() {
        if (ritualItem == null || worldObj.isRemote) {
            return;
        }
        if (!revealed) {
            applyEnchantments();
        }
        dropRitualItemAt(xCoord + 0.5D, yCoord + 0.8D, zCoord + 0.5D);
        resetRitual();
    }

    private void dropRitualItemAt(double x, double y, double z) {
        EntityItem entity = new EntityItem(worldObj, x, y, z, ritualItem);
        entity.motionX = 0.0D;
        entity.motionY = 0.1D;
        entity.motionZ = 0.0D;
        entity.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(entity);
        ritualItem = null;
    }

    private void resetRitual() {
        ritualItem = null;
        ritualAge = -1;
        revealed = false;
        pendingEnchants = null;
        ritualPlayer = null;
    }

    private void sync() {
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    private void fireEvent(int id, int param) {
        worldObj.addBlockEvent(xCoord, yCoord, zCoord, getBlockType(), id, param);
    }

    @Override
    public boolean receiveClientEvent(int id, int param) {
        if (id < EVT_RITUAL_START || id > EVT_RESCRY) {
            return super.receiveClientEvent(id, param);
        }
        if (worldObj != null && worldObj.isRemote) {
            ArcaneEnchanting.proxy.onTableEvent(this, id, param);
        }
        // Returning true on the server makes the event reach the clients.
        return true;
    }

    // ------------------------------------------------------------------ persistence and sync

    private void writeSync(NBTTagCompound tag) {
        if (ritualItem != null && ritualAge >= 0) {
            tag.setTag("RitualItem", ritualItem.writeToNBT(new NBTTagCompound()));
            tag.setInteger("RitualAge", ritualAge);
            tag.setInteger("RitualDuration", ritualDuration);
            tag.setInteger("RitualTier", ritualTier);
            tag.setBoolean("Revealed", revealed);
        }
    }

    private void readSync(NBTTagCompound tag) {
        if (tag.hasKey("RitualItem")) {
            ritualItem = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("RitualItem"));
            ritualAge = tag.getInteger("RitualAge");
            ritualDuration = tag.getInteger("RitualDuration");
            ritualTier = tag.getInteger("RitualTier");
            revealed = tag.getBoolean("Revealed");
        } else {
            ritualItem = null;
            ritualAge = -1;
            revealed = false;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeSync(tag);
        if (ritualItem != null && ritualAge >= 0) {
            if (pendingEnchants != null) {
                tag.setTag("PendingEnchants", pendingEnchants);
            }
            tag.setBoolean("PendingIsBook", pendingIsBook);
            if (ritualPlayer != null) {
                tag.setLong("PlayerMost", ritualPlayer.getMostSignificantBits());
                tag.setLong("PlayerLeast", ritualPlayer.getLeastSignificantBits());
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        readSync(tag);
        pendingEnchants = tag.hasKey("PendingEnchants") ? tag.getTagList("PendingEnchants", 10) : null;
        pendingIsBook = tag.getBoolean("PendingIsBook");
        ritualPlayer = tag.hasKey("PlayerMost") ? new UUID(tag.getLong("PlayerMost"), tag.getLong("PlayerLeast"))
            : null;
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        writeSync(tag);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        readSync(packet.func_148857_g());
    }

    // ------------------------------------------------------------------ rendering hints

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0 || pass == 1;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        double top = isRitualActive() || anim.climaxAge >= 0 ? 16.0D : 4.0D;
        return AxisAlignedBB.getBoundingBox(
            xCoord - 3.0D,
            yCoord - 0.5D,
            zCoord - 3.0D,
            xCoord + 4.0D,
            yCoord + top,
            zCoord + 4.0D);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 96.0D * 96.0D;
    }
}
