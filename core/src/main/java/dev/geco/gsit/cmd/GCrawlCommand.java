package dev.geco.gsit.cmd;

import org.jetbrains.annotations.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;

import dev.geco.gsit.GSitMain;
import dev.geco.gsit.objects.*;

public class GCrawlCommand implements CommandExecutor {

    private final GSitMain GPM;

    public GCrawlCommand(GSitMain GPluginMain) { GPM = GPluginMain; }

    @Override
    public boolean onCommand(@NotNull CommandSender Sender, @NotNull Command Command, @NotNull String Label, String[] Args) {

        if(!(Sender instanceof Player)) {

            GPM.getMManager().sendMessage(Sender, "Messages.command-sender-error");
            return true;
        }

        Player player = (Player) Sender;

        if(!GPM.getPManager().hasNormalPermission(Sender, "Crawl")) {

            GPM.getMManager().sendMessage(Sender, "Messages.command-permission-error");
            return true;
        }

        if(GPM.getCrawlManager() == null) {

            String v = Bukkit.getServer().getClass().getPackage().getName();
            v = v.substring(v.lastIndexOf('.') + 1);

            GPM.getMManager().sendMessage(Sender, "Messages.command-version-error", "%Version%", v);
            return true;
        }

        if(GPM.getCrawlManager().isCrawling(player)) {

            GPM.getCrawlManager().stopCrawl(player, GetUpReason.GET_UP);
            return true;
        }

        if(!player.isValid() || player.isSneaking() || !player.isOnGround() || player.isInsideVehicle() || player.isSleeping()) {

            GPM.getMManager().sendMessage(Sender, "Messages.action-crawl-now-error");
            return true;
        }

        if(GPM.getCManager().WORLDBLACKLIST.contains(player.getWorld().getName()) && !GPM.getPManager().hasPermission(Sender, "ByPass.World", "ByPass.*")) {

            GPM.getMManager().sendMessage(Sender, "Messages.action-crawl-world-error");
            return true;
        }

        if(GPM.getWorldGuardLink() != null && !GPM.getWorldGuardLink().checkFlag(player.getLocation(), GPM.getWorldGuardLink().CRAWL_FLAG)) {

            GPM.getMManager().sendMessage(Sender, "Messages.action-crawl-region-error");
            return true;
        }

        if(GPM.getCrawlManager().startCrawl(player) == null) GPM.getMManager().sendMessage(Sender, "Messages.action-crawl-region-error");
        return true;
    }

}