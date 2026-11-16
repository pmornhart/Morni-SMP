package net.morni.smp.net;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Verbindung zum Netzwerk morni.net. Läuft das Netzwerk-Plugin („MorniNet“), geht alles über dessen Schnittstelle
 * ({@code net.morni.api.MorniNet}); sonst läuft MorniSMP allein: deutsche Texte, keine Netzwerk-Statistik, „zur
 * Lobby“ trennt die Verbindung mit einem freundlichen Hinweis.
 *
 * <p>Nur {@link Connected} kennt die Netzwerk-Klassen – ohne Netzwerk-Plugin wird sie nie geladen.
 */
public interface Network {

    /** Kennung des Modus im Netzwerk (historisch „mirni“; angezeigt wird „MorniSMP“). */
    String MODE = "mirni";

    /** Läuft das Netzwerk-Plugin? */
    boolean connected();

    /** „de“ oder „en“. */
    String language(UUID player);

    /** Hat der Rang dieses Recht? (ohne Netzwerk: nie) */
    boolean hasRight(UUID player, String right);

    /** Statistiken melden (hochzählen + Bestwerte). */
    void stats(UUID player, Map<String, Long> add, Map<String, Long> best);

    /** Zustand für Lobby-Figur und Webseite. */
    void state(String state, int players);

    /** Zurück in die Lobby (ohne Netzwerk: Verbindung trennen mit {@code message}). */
    void toLobby(Player player, Component message);

    /** Ist der Hub gerade verbunden? (nur dann kommen Statistiken an) */
    boolean hubOnline();

    /** Netzwerk suchen: mit Plugin „MorniNet“ die echte Verbindung, sonst allein. */
    static Network connect(Plugin plugin) {
        Logger log = plugin.getLogger();
        if (plugin.getServer().getPluginManager().getPlugin("MorniNet") != null) {
            try {
                Network n = Connected.create();
                if (n != null) {
                    return n;
                }
                log.warning("Netzwerk-Plugin gefunden, aber ohne Schnittstelle für Spielmodi – MorniSMP läuft allein.");
            } catch (LinkageError | RuntimeException e) {
                log.warning("Netzwerk-Schnittstelle passt nicht (" + e + ") – MorniSMP läuft allein.");
            }
        }
        return new Standalone();
    }
}
