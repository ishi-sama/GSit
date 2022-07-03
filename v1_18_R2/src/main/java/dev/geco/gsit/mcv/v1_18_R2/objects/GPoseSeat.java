package dev.geco.gsit.mcv.v1_18_R2.objects;

import java.util.*;

import com.mojang.authlib.*;
import com.mojang.datafixers.util.*;

import org.bukkit.*;
import org.bukkit.block.data.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.*;
import org.bukkit.craftbukkit.v1_18_R2.*;
import org.bukkit.craftbukkit.v1_18_R2.entity.*;
import org.bukkit.craftbukkit.v1_18_R2.inventory.*;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

import dev.geco.gsit.GSitMain;
import dev.geco.gsit.objects.*;

public class GPoseSeat implements IGPoseSeat {

    private final GSitMain GPM = GSitMain.getInstance();

    private final GSeat seat;
    private final Pose pose;

    private Set<Player> nearPlayers = new HashSet<>();

    private final ServerPlayer serverPlayer;
    protected final ServerPlayer playerNpc;

    private final Location blockLocation;

    private final BlockData bedData;
    private final BlockPos bedPos;

    private final Direction direction;

    protected ClientboundBlockUpdatePacket setBedPacket;
    protected ClientboundPlayerInfoPacket addNpcInfoPacket;
    protected ClientboundPlayerInfoPacket removeNpcInfoPacket;
    protected ClientboundRemoveEntitiesPacket removeNpcPacket;
    protected ClientboundAddPlayerPacket createNpcPacket;
    protected ClientboundSetEntityDataPacket metaNpcPacket;
    protected ClientboundTeleportEntityPacket teleportNpcPacket;
    protected ClientboundMoveEntityPacket.PosRot rotateNpcPacket;

    private BukkitRunnable task;

    private final Listener listener;

    public GPoseSeat(GSeat Seat, Pose Pose) {

        seat = Seat;
        pose = Pose;

        Location seatLocation = seat.getLocation();

        serverPlayer = ((CraftPlayer) seat.getPlayer()).getHandle();

        playerNpc = createNPC();
        playerNpc.moveTo(seatLocation.getX(), seatLocation.getY() + (pose == org.bukkit.entity.Pose.SLEEPING ? 0.3125d : pose == org.bukkit.entity.Pose.SPIN_ATTACK ? 0.2d : 0d), seatLocation.getZ(), 0f, 0f);

        blockLocation = seatLocation.clone();
        blockLocation.setY(blockLocation.getWorld().getMinHeight());

        bedData = blockLocation.getBlock().getBlockData();
        bedPos = new BlockPos(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());

        direction = getDirection();

        setNpcMeta();

        if(pose == org.bukkit.entity.Pose.SLEEPING) setBedPacket = new ClientboundBlockUpdatePacket(bedPos, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, direction.getOpposite()).setValue(BedBlock.PART, BedPart.HEAD));
        addNpcInfoPacket = new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, playerNpc);
        removeNpcInfoPacket = new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, playerNpc);
        removeNpcPacket = new ClientboundRemoveEntitiesPacket(playerNpc.getId());
        createNpcPacket = new ClientboundAddPlayerPacket(playerNpc);
        metaNpcPacket = new ClientboundSetEntityDataPacket(playerNpc.getId(), playerNpc.getEntityData(), false);
        if(pose == org.bukkit.entity.Pose.SLEEPING) teleportNpcPacket = new ClientboundTeleportEntityPacket(playerNpc);
        if(pose == org.bukkit.entity.Pose.SPIN_ATTACK) rotateNpcPacket = new ClientboundMoveEntityPacket.PosRot(playerNpc.getId(), (short) 0, (short) 0, (short) 0, (byte) 0, getFixedRotation(-90.0f), true);

        listener = new Listener() {

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void PIntE(PlayerInteractEvent Event) { if(Event.getPlayer() == seat.getPlayer() && !GPM.getCManager().P_INTERACT) Event.setCancelled(true); }

            @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
            public void PIntE(PlayerInteractEntityEvent Event) { if(Event.getPlayer() == seat.getPlayer()) Event.setCancelled(true); }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void EDamBEE(EntityDamageByEntityEvent Event) { if(Event.getDamager() == seat.getPlayer() && !GPM.getCManager().P_INTERACT) Event.setCancelled(true); }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void EDamE(EntityDamageEvent Event) { if(Event.getEntity() == seat.getPlayer()) playAnimation(1); }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void PLauE(ProjectileLaunchEvent Event) { if(Event.getEntity().getShooter() == seat.getPlayer() && !GPM.getCManager().P_INTERACT) Event.setCancelled(true); }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void PAniE(PlayerAnimationEvent Event) { if(Event.getPlayer() == seat.getPlayer() && Event.getAnimationType() == PlayerAnimationType.ARM_SWING) playAnimation(Event.getPlayer().getMainHand().equals(MainHand.RIGHT) ? 0 : 3); }

            @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
            public void PGamMCE(PlayerGameModeChangeEvent Event) { if(Event.getPlayer() == seat.getPlayer() && Event.getNewGameMode() == GameMode.CREATIVE) setEquipmentVisibility(true); }
        };
    }

    public void spawn() {

        nearPlayers = getNearPlayers();

        playerNpc.setGlowingTag(serverPlayer.hasGlowingTag());
        if(serverPlayer.hasGlowingTag()) serverPlayer.setGlowingTag(false);

        serverPlayer.setInvisible(true);

        setEquipmentVisibility(false);

        playerNpc.getEntityData().set(EntityDataSerializers.COMPOUND_TAG.createAccessor(19), serverPlayer.getEntityData().get(EntityDataSerializers.COMPOUND_TAG.createAccessor(19)));
        playerNpc.getEntityData().set(EntityDataSerializers.COMPOUND_TAG.createAccessor(20), serverPlayer.getEntityData().get(EntityDataSerializers.COMPOUND_TAG.createAccessor(20)));
        serverPlayer.getEntityData().set(EntityDataSerializers.COMPOUND_TAG.createAccessor(19), new CompoundTag());
        serverPlayer.getEntityData().set(EntityDataSerializers.COMPOUND_TAG.createAccessor(20), new CompoundTag());

        if(pose == Pose.SLEEPING) {

            if(GPM.getCManager().P_LAY_NIGHT_SKIP) seat.getPlayer().setSleepingIgnored(true);

            if(GPM.getCManager().P_LAY_REST) seat.getPlayer().setStatistic(Statistic.TIME_SINCE_REST, 0);
        }

        for(Player nearPlayer : nearPlayers) spawnToPlayer(nearPlayer);

        Bukkit.getPluginManager().registerEvents(listener, GPM);

        startUpdate();
    }

    private void spawnToPlayer(Player Player) {

        ServerPlayer spawnPlayer = ((CraftPlayer) Player).getHandle();

        spawnPlayer.connection.send(addNpcInfoPacket);
        spawnPlayer.connection.send(createNpcPacket);
        if(pose == Pose.SLEEPING) spawnPlayer.connection.send(setBedPacket);
        spawnPlayer.connection.send(metaNpcPacket);
        if(pose == Pose.SLEEPING) spawnPlayer.connection.send(teleportNpcPacket);
        if(pose == Pose.SPIN_ATTACK) spawnPlayer.connection.send(rotateNpcPacket);

        new BukkitRunnable() {

            @Override
            public void run() {

                spawnPlayer.connection.send(removeNpcInfoPacket);
            }
        }.runTaskLater(GPM, 15);
    }

    public void remove() {

        stopUpdate();

        HandlerList.unregisterAll(listener);

        for(Player nearPlayer : nearPlayers) removeToPlayer(nearPlayer);

        if(pose == Pose.SLEEPING && GPM.getCManager().P_LAY_NIGHT_SKIP) seat.getPlayer().setSleepingIgnored(false);

        serverPlayer.setInvisible(false);

        setEquipmentVisibility(true);

        seat.getPlayer().setInvisible(false);

        serverPlayer.getEntityData().set(EntityDataSerializers.COMPOUND_TAG.createAccessor(19), playerNpc.getEntityData().get(EntityDataSerializers.COMPOUND_TAG.createAccessor(19)));
        serverPlayer.getEntityData().set(EntityDataSerializers.COMPOUND_TAG.createAccessor(20), playerNpc.getEntityData().get(EntityDataSerializers.COMPOUND_TAG.createAccessor(20)));

        serverPlayer.setGlowingTag(playerNpc.hasGlowingTag());
    }

    private void removeToPlayer(Player Player) {

        ServerPlayer removePlayer = ((CraftPlayer) Player).getHandle();

        removePlayer.connection.send(removeNpcInfoPacket);
        removePlayer.connection.send(removeNpcPacket);

        Player.sendBlockChange(blockLocation, bedData);
    }

    private Set<Player> getNearPlayers() {

        HashSet<Player> playerList = new HashSet<>();
        seat.getLocation().getWorld().getPlayers().stream().filter(o -> seat.getLocation().distance(o.getLocation()) <= 250 && o.canSee(seat.getPlayer())).forEach(playerList::add);
        return playerList;
    }

    private void startUpdate() {

        task = new BukkitRunnable() {

            long sleepTick = 0;

            @Override
            public void run() {

                Set<Player> playerList = getNearPlayers();

                for(Player nearPlayer : playerList) {

                    if(nearPlayers.contains(nearPlayer)) continue;

                    nearPlayers.add(nearPlayer);

                    spawnToPlayer(nearPlayer);
                }

                for(Player nearPlayer : new HashSet<>(nearPlayers)) {

                    if(playerList.contains(nearPlayer)) continue;

                    nearPlayers.remove(nearPlayer);

                    removeToPlayer(nearPlayer);
                }

                if(pose != Pose.SPIN_ATTACK) updateDirection();

                serverPlayer.setInvisible(true);

                updateEquipment();

                setEquipmentVisibility(false);

                updateSkin();

                if(pose == Pose.SLEEPING) {

                    for(Player nearPlayer : nearPlayers) ((CraftPlayer) nearPlayer).getHandle().connection.send(setBedPacket);

                    if(GPM.getCManager().P_LAY_SNORING_SOUNDS) {

                        sleepTick++;

                        if(sleepTick >= 90) {

                            long tick = seat.getPlayer().getPlayerTime();

                            if(!GPM.getCManager().P_LAY_SNORING_NIGHT_ONLY || (tick >= 12500 && tick <= 23500)) for(Player nearPlayer : nearPlayers) nearPlayer.playSound(seat.getLocation(), Sound.ENTITY_FOX_SLEEP, SoundCategory.PLAYERS, 1.5f, 0);

                            sleepTick = 0;
                        }
                    }
                }
            }
        };

        task.runTaskTimerAsynchronously(GPM, 5, 1);
    }

    private void stopUpdate() { if(task != null && !task.isCancelled()) task.cancel(); }

    private void setNpcMeta() {

        playerNpc.getEntityData().set(EntityDataSerializers.POSE.createAccessor(6), net.minecraft.world.entity.Pose.values()[pose.ordinal()]);
        playerNpc.getEntityData().set(EntityDataSerializers.BYTE.createAccessor(17), serverPlayer.getEntityData().get(EntityDataSerializers.BYTE.createAccessor(17)));
        playerNpc.getEntityData().set(EntityDataSerializers.BYTE.createAccessor(18), serverPlayer.getEntityData().get(EntityDataSerializers.BYTE.createAccessor(18)));
        if(pose == Pose.SPIN_ATTACK) playerNpc.getEntityData().set(EntityDataSerializers.BYTE.createAccessor(8), (byte) 4);
        if(pose == Pose.SLEEPING) playerNpc.getEntityData().set(EntityDataSerializers.OPTIONAL_BLOCK_POS.createAccessor(14), Optional.of(bedPos));
    }

    private float fixYaw(float Yaw) { return (Yaw < 0.0f ? 360.0f + Yaw : Yaw) % 360.0f; }

    private void updateDirection() {

        if(pose == Pose.SWIMMING) {

            byte fixedRotation = getFixedRotation(seat.getPlayer().getLocation().getYaw());

            ClientboundRotateHeadPacket rotateHeadPacket = new ClientboundRotateHeadPacket(playerNpc, fixedRotation);
            ClientboundMoveEntityPacket.PosRot moveEntityPacket = new ClientboundMoveEntityPacket.PosRot(playerNpc.getId(), (short) 0, (short) 0, (short) 0, fixedRotation, (byte) 0, true);

            for(Player nearPlayer : nearPlayers) {

                ServerPlayer player = ((CraftPlayer) nearPlayer).getHandle();

                player.connection.send(rotateHeadPacket);
                player.connection.send(moveEntityPacket);
            }

            return;
        }

        float playerYaw = seat.getPlayer().getLocation().getYaw();

        if(direction == Direction.WEST) playerYaw -= 90;
        if(direction == Direction.EAST) playerYaw += 90;
        if(direction == Direction.NORTH) playerYaw -= 180;

        playerYaw = fixYaw(playerYaw);

        byte fixedRotation = getFixedRotation(playerYaw >= 315 ? playerYaw - 360 : playerYaw <= 45 ? playerYaw : playerYaw >= 180 ? -45 : 45);

        ClientboundRotateHeadPacket rotateHeadPacket = new ClientboundRotateHeadPacket(playerNpc, fixedRotation);

        for(Player nearPlayer : nearPlayers) ((CraftPlayer) nearPlayer).getHandle().connection.send(rotateHeadPacket);
    }

    private void updateSkin() {

        SynchedEntityData entityData = playerNpc.getEntityData();

        entityData.set(EntityDataSerializers.BYTE.createAccessor(17), serverPlayer.getEntityData().get(EntityDataSerializers.BYTE.createAccessor(17)));
        entityData.set(EntityDataSerializers.BYTE.createAccessor(18), serverPlayer.getEntityData().get(EntityDataSerializers.BYTE.createAccessor(18)));

        ClientboundSetEntityDataPacket entityDataPacket = new ClientboundSetEntityDataPacket(playerNpc.getId(), entityData, false);

        for(Player nearPlayer : nearPlayers) ((CraftPlayer) nearPlayer).getHandle().connection.send(entityDataPacket);
    }

    private void updateEquipment() {

        List<Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> equipmentList = new ArrayList<>();

        for(net.minecraft.world.entity.EquipmentSlot equipmentSlot : net.minecraft.world.entity.EquipmentSlot.values()) {

            net.minecraft.world.item.ItemStack itemStack = serverPlayer.getItemBySlot(equipmentSlot);

            if(itemStack != null) equipmentList.add(Pair.of(equipmentSlot, itemStack));
        }

        ClientboundSetEquipmentPacket setEquipmentPacket = new ClientboundSetEquipmentPacket(playerNpc.getId(), equipmentList);

        for(Player nearPlayer : nearPlayers) ((CraftPlayer) nearPlayer).getHandle().connection.send(setEquipmentPacket);
    }

    private void setEquipmentVisibility(boolean Visibility) {

        List<Pair<net.minecraft.world.entity.EquipmentSlot, net.minecraft.world.item.ItemStack>> equipmentList = new ArrayList<>();

        net.minecraft.world.item.ItemStack nmsCopy = CraftItemStack.asNMSCopy(new ItemStack(Material.AIR));

        for(net.minecraft.world.entity.EquipmentSlot equipmentSlot : net.minecraft.world.entity.EquipmentSlot.values()) {

            net.minecraft.world.item.ItemStack itemStack = Visibility ? serverPlayer.getItemBySlot(equipmentSlot) : null;

            equipmentList.add(Pair.of(equipmentSlot, itemStack != null ? itemStack : nmsCopy));
        }

        ClientboundSetEquipmentPacket setEquipmentPacket = new ClientboundSetEquipmentPacket(serverPlayer.getId(), equipmentList);

        for(Player nearPlayer : nearPlayers) {

            if(nearPlayer == seat.getPlayer()) continue;

            ((CraftPlayer) nearPlayer).getHandle().connection.send(setEquipmentPacket);
        }

        if(seat.getPlayer().getGameMode() != GameMode.CREATIVE) {

            seat.getPlayer().updateInventory();

            if(!Visibility) serverPlayer.connection.send(setEquipmentPacket);
        }
    }

    private void playAnimation(int Arm) {

        ClientboundAnimatePacket animatePacket = new ClientboundAnimatePacket(playerNpc, Arm);

        for(Player nearPlayer : nearPlayers) ((CraftPlayer) nearPlayer).getHandle().connection.send(animatePacket);
    }

    private byte getFixedRotation(float Yaw) { return (byte) (Yaw * 256.0f / 360.0f); }

    private Direction getDirection() {

        float yaw = seat.getLocation().getYaw();

        return (yaw >= 135f || yaw < -135f) ? Direction.NORTH : (yaw >= -135f && yaw < -45f) ? Direction.EAST : (yaw >= -45f && yaw < 45f) ? Direction.SOUTH : yaw >= 45f ? Direction.WEST : Direction.NORTH;
    }

    private ServerPlayer createNPC() {

        MinecraftServer minecraftServer = ((CraftServer) Bukkit.getServer()).getServer();

        ServerLevel serverLevel = ((CraftWorld) seat.getLocation().getWorld()).getHandle();

        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), seat.getPlayer().getName());

        gameProfile.getProperties().putAll(serverPlayer.getGameProfile().getProperties());

        return new ServerPlayer(minecraftServer, serverLevel, gameProfile);
    }

    public GSeat getSeat() { return seat; }

    public Pose getPose() { return pose; }

    public String toString() { return seat.toString(); }

}