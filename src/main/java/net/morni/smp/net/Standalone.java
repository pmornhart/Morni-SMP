package net.morni.smp.net;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/** MorniSMP ohne Netzwerk: deutsch, keine Statistik nach außen, „zur Lobby“ = freundlich trennen. */
final class Standalone implements Network {

    @Override
    public boolean connected() {
        return false;
    }

    @Override
    public String language(UUID player) {
        return "de";
    }

    @Override
    public boolean hasRight(UUID player, String right) {
        return false;
    }

    @Override
    public void stats(UUID player, Map<String, Long> add, Map<String, Long> best) {
        // ohne Netzwerk zählt MorniSMP nur lokal
    }

    @Override
    public void state(String state, int players) {
        // niemand fragt
    }

    @Override
    public void toLobby(Player player, Component message) {
        player.kick(message);
    }

    @Override
    public boolean hubOnline() {
        return false;
    }
}
