package de.moinmorni.smp.command;

import de.moinmorni.smp.Smp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Verteilt die Befehle aus der plugin.yml an ihre Bausteine; Fehler landen im Protokoll, der Spieler liest „Ups“. */
public final class Router implements TabExecutor {

    /** Ein Befehl. */
    public interface Handler {
        void run(CommandSender sender, String[] args);

        default List<String> tab(CommandSender sender, String[] args) {
            return null;
        }

        /** Nur für Spieler (nicht Konsole)? */
        default boolean playersOnly() {
            return true;
        }
    }

    private final Smp s;
    private final Map<String, Handler> handlers = new HashMap<>();

    public Router(Smp s) {
        this.s = s;
    }

    public void add(JavaPlugin plugin, String name, Handler h) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("Befehl /" + name + " fehlt in der plugin.yml");
            return;
        }
        handlers.put(name, h);
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Handler h = handlers.get(command.getName().toLowerCase(Locale.ROOT));
        if (h == null) {
            return false;
        }
        if (h.playersOnly() && !(sender instanceof Player)) {
            s.msg.send(sender, "command.players-only");
            return true;
        }
        try {
            h.run(sender, args);
        } catch (RuntimeException e) {
            s.plugin.getLogger().log(Level.SEVERE, "Fehler bei /" + label + " " + String.join(" ", args), e);
            s.msg.send(sender, "command.error");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Handler h = handlers.get(command.getName().toLowerCase(Locale.ROOT));
        List<String> out = h == null ? null : h.tab(sender, args);
        if (out == null) {
            return null;
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String o : out) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                filtered.add(o);
            }
        }
        return filtered;
    }

    /** Namen der Spieler online (für Vorschläge). */
    public static List<String> online() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }
}
