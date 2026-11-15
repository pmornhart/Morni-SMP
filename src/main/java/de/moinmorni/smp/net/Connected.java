package de.moinmorni.smp.net;

import de.moinmorni.api.MoinMorni;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * MorniSMP im Netzwerk: alles über die Schnittstelle des Netzwerk-Plugins. Die Schnittstelle wird bei jedem Aufruf neu
 * geholt – lädt jemand das Netzwerk-Plugin neu, stimmt sie trotzdem.
 */
final class Connected implements Network {

    private static final String GERMAN = "de";

    private Connected() {
    }

    /** null, wenn das Netzwerk-Plugin keine Schnittstelle anbietet. */
    static Network create() {
        return MoinMorni.get().isPresent() ? new Connected() : null;
    }

    private static Optional<MoinMorni> api() {
        return MoinMorni.get();
    }

    @Override
    public boolean connected() {
        return true;
    }

    @Override
    public String language(UUID player) {
        String lang = api().map(a -> a.language(player)).orElse(GERMAN);
        return "en".equals(lang) ? "en" : GERMAN;
    }

    @Override
    public boolean hasRight(UUID player, String right) {
        return api().map(a -> a.hasRight(player, right)).orElse(false);
    }

    @Override
    public void stats(UUID player, Map<String, Long> add, Map<String, Long> best) {
        api().ifPresent(a -> a.stats(MODE, player, add, best, false));
    }

    @Override
    public void state(String state, int players) {
        api().ifPresent(a -> a.state(MODE, state, players));
    }

    @Override
    public void toLobby(Player player, Component message) {
        api().ifPresentOrElse(a -> a.toLobby(player), () -> player.kick(message));
    }

    @Override
    public boolean hubOnline() {
        return api().map(MoinMorni::online).orElse(false);
    }
}
