package dev.geco.gsit.mcv.v1_18_R2.util;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftEntity;

import dev.geco.gsit.objects.*;
import dev.geco.gsit.mcv.v1_18_R2.objects.*;

public class SpawnUtil implements ISpawnUtil {

    public boolean needCheck() { return false; }

    public boolean checkLocation(Location Location) { return true; }

    public boolean checkPlayerLocation(Entity Holder) { return true; }

    public Entity createSeatEntity(Location Location, Entity Rider) {

        SeatEntity sas = new SeatEntity(Location);

        if(Rider != null && Rider.isValid()) ((CraftEntity) Rider).getHandle().startRiding(sas, true);

        ((CraftWorld) Location.getWorld()).getHandle().entityManager.addNewEntity(sas);

        return sas.getBukkitEntity();
    }

    public Entity createPlayerSeatEntity(Entity Holder, Entity Rider) {

        PlayerSeatEntity sas = new PlayerSeatEntity(Holder.getLocation());

        if(Rider != null && Rider.isValid()) {

            sas.startRiding(((CraftEntity) Holder).getHandle(), true);

            ((CraftEntity) Rider).getHandle().startRiding(sas, true);
        }

        ((CraftWorld) Holder.getWorld()).getHandle().entityManager.addNewEntity(sas);

        return sas.getBukkitEntity();
    }

}