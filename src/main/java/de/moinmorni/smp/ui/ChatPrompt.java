package de.moinmorni.smp.ui;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Eingabe über den Chat (Suchtext, Preis, Menge): Die nächste Chat-Zeile des Spielers geht nicht an alle, sondern an
 * die wartende Frage. „abbrechen“/„cancel“ bricht ab; nach 60 Sekunden verfällt die Frage. Geht auch auf Bedrock.
 */
public final class ChatPrompt implements Listener {

    private record Pending(Consumer<String> answer, Runnable cancelled, long until) {
    }

    private static final long TIMEOUT_MS = 60_000;

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Auf die nächste Chat-Zeile warten; {@code answer} läuft im Haupt-Thread. */
    public void ask(Player p, Consumer<String> answer, Runnable cancelled) {
        pending.put(p.getUniqueId(), new Pending(answer, cancelled, System.currentTimeMillis() + TIMEOUT_MS));
    }

    public boolean waiting(Player p) {
        Pending w = pending.get(p.getUniqueId());
        return w != null && w.until() > System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending w = pending.remove(e.getPlayer().getUniqueId());
        if (w == null || w.until() < System.currentTimeMillis()) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("abbrechen") || text.equalsIgnoreCase("cancel") || text.isEmpty()) {
                if (w.cancelled() != null) {
                    w.cancelled().run();
                }
            } else {
                w.answer().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
