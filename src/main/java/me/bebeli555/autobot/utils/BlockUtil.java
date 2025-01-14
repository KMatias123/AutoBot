package me.bebeli555.autobot.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;

import me.bebeli555.autobot.AutoBot;
import me.bebeli555.autobot.mods.Mods;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;

public class BlockUtil extends Mods {
	
	/**
	 * Searches around the player to find the given block.
	 * @radius the radius to search around the player
	 */
	public static BlockPos findBlock(Block block, int radius) {
        for (int x = (int) (mc.player.posX - radius); x < mc.player.posX + radius; x++) {
            for (int z = (int) (mc.player.posZ - radius); z < mc.player.posZ + radius; z++) {
                for (int y = (int) (mc.player.posY + radius); y > mc.player.posY - radius; y--) {
                	BlockPos pos = new BlockPos(x, y, z);
                	if (mc.world.getBlockState(pos).getBlock().equals(block)) {
                		return pos;
                	}
                }
            }
        }
		
		return null;
	}
	
	/**
	 * Gets all the BlockPositions in the given radius around the player
	 */
	public static List<BlockPos> getAll(int radius) {
		List<BlockPos> list = new ArrayList<>();
        for (int x = (int) (mc.player.posX - radius); x < mc.player.posX + radius; x++) {
            for (int z = (int) (mc.player.posZ - radius); z < mc.player.posZ + radius; z++) {
                for (int y = (int) (mc.player.posY + radius); y > mc.player.posY - radius; y--) {
            		list.add(new BlockPos(x, y, z));
                }
            }
        }
        
        Collections.sort(list, (lhs, rhs) -> mc.player.getDistanceSq(lhs) > mc.player.getDistanceSq(rhs) ? 1 : (mc.player.getDistanceSq(lhs) < mc.player.getDistanceSq(rhs)) ? -1 : 0);
        
        return list;
	}
	
	/**
	 * Gets all the BlockPositions in the given radius around the pos
	 */
	public static ArrayList<BlockPos> getAll(Vec3d pos, int radius) {
		ArrayList<BlockPos> list = new ArrayList<BlockPos>();
		
        for (int x = (int) (pos.x - radius); x < pos.x + radius; x++) {
            for (int z = (int) (pos.z - radius); z < pos.z + radius; z++) {
                for (int y = (int) (pos.y + radius); y > pos.y - radius; y--) {
            		list.add(new BlockPos(x, y, z));
                }
            }
        }
        
        return list;
	}
	
	/**
	 * Checks if a block can be placed to this position
	 */
	public static boolean canPlaceBlock(BlockPos pos) {
		try {
			for (Entity entity : mc.world.getEntitiesWithinAABBExcludingEntity(null, new AxisAlignedBB(pos))) {
				if (!entity.equals(mc.player)) {
					return false;
				}
			}
		} catch (ConcurrentModificationException ignored) {
			
		}
		
		return !isSolid(pos);
	}
	
	/**
	 * Places a block to the given BlockPosition
	 * This is run on the client thread
	 * @pos Places the block to this position
	 * @block The block to place. Must be in ur inventory!
	 */
	public static boolean placeBlock(Block block, BlockPos pos, boolean spoofRotation) {
		Place place = new Place(null, block, pos, spoofRotation);
		sleepUntil(() -> place.done, -1);
		return place.success;
	}
	
	public static void placeBlockNoSleep(Block block, BlockPos pos, boolean spoofRotation) {
		new Place(null, block, pos, spoofRotation);
	}
	
	/**
	 * Same as the placeBlock but it interacts with the given block with the given item
	 * This is run on the client thread
	 */
	public static boolean placeItem(Item item, BlockPos pos, boolean spoofRotation) {
		Place place = new Place(item, null, pos, spoofRotation);
		sleepUntil(() -> place.done, -1);
		return place.success;
	}
	
	public static void placeItemNoSleep(Item item, BlockPos pos, boolean spoofRotation) {
		new Place(item, null, pos, spoofRotation);
	}
	
	/**
	 * Distance between these 2 blockpositions
	 */
	public static int distance(BlockPos first, BlockPos second) {
		return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ());
	}
	
	/**
	 * Checks if the block is in render distance or known by the client.
	 */
	public static boolean isInRenderDistance(BlockPos pos) {
		return mc.world.getChunk(pos).isLoaded();
	}
	
	/**
	 * Checks if any neighbor block can be right clicked
	 */
	public static boolean canBeClicked(BlockPos pos) {
		for (EnumFacing facing : EnumFacing.values()) {
			BlockPos neighbor = pos.offset(facing);

			//If neighbor cant be clicked then continue
            if (!mc.world.getBlockState(neighbor).getBlock().canCollideCheck(mc.world.getBlockState(neighbor), false) && getBlock(neighbor) != Blocks.WATER) {
               continue;
            }
            
            return true;
		}
		
		return false;
	}
	
	public static class Place {
		public boolean done, success, spoofRotation;
		public Item item;
		public Block block;
		public BlockPos pos;
		
		public Place(Item item, Block block, BlockPos pos, boolean spoofRotation) {
			this.item = item;
			this.pos = pos;
			this.block = block;
			this.spoofRotation = spoofRotation;
			MinecraftForge.EVENT_BUS.register(this);
		}
		
		@SubscribeEvent
		public void onTick(ClientTickEvent e) {
			if (block != null || item != null) {
				int slot = -1;
				if (block != null) {
					slot = InventoryUtil.getSlot(block);
				} else if (item != null) {
					slot = InventoryUtil.getSlot(item);
				}
				 
				//If item wasent found on inventory then return false
				if (slot == -1) {
					done(false);
					return;
				}
				
				//Put the given item into the hand so it can be "placed"
				if (InventoryUtil.getHandSlot() != slot) {
					InventoryUtil.switchItem(slot, false);
				}
			}
			
			for (EnumFacing facing : EnumFacing.values()) {
				BlockPos neighbor = pos.offset(facing);
				EnumFacing side = facing.getOpposite();

				//If neighbor cant be clicked then continue
	            if (!mc.world.getBlockState(neighbor).getBlock().canCollideCheck(mc.world.getBlockState(neighbor), false) && getBlock(neighbor) != Blocks.WATER) {
	               continue;
	            }
				
				Vec3d hitVec;
				if (item != null) {
					hitVec = new Vec3d(pos).add(0.5, 0.5, 0.5).add(new Vec3d(side.getDirectionVec()).scale(0.5));
				} else {
					hitVec = new Vec3d(neighbor).add(0.5, 0.5, 0.5).add(new Vec3d(side.getDirectionVec()).scale(0.5));
				}
				
				if (spoofRotation) {
					RotationUtil.rotateSpoof(hitVec);
				} else {
					RotationUtil.rotate(hitVec, true);
				}
				mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING));
		        if (item != null) {
		        	mc.playerController.processRightClickBlock(mc.player, mc.world, pos, side, hitVec, EnumHand.MAIN_HAND);
		        } else {
		        	mc.playerController.processRightClickBlock(mc.player, mc.world, neighbor, side, hitVec, EnumHand.MAIN_HAND);
		        	mc.player.swingArm(EnumHand.MAIN_HAND);
		        }
				mc.player.connection.sendPacket(new CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING));
				done(true);
				return;
			}
			
			done(false);
			return;
		}
		
		public void done(boolean success) {
			this.done = true;
			this.success = success;
			MinecraftForge.EVENT_BUS.unregister(this);
			RotationUtil.stopRotating();
		}
	}
}
