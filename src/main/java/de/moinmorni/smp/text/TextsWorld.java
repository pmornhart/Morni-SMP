package de.moinmorni.smp.text;

/** Texte – Welt: Zufalls-Teleport, Grundstücke, Schutz. */
final class TextsWorld {

    private TextsWorld() {
    }

    private static void t(String key, String de, String en) {
        Texts.t(key, de, en);
    }

    static void register() {
        // Zufalls-Teleport
        t("rtp.title", "Reisetor – wohin?", "Travel gate – where to?");
        t("rtp.nah", "<em><b>Nah</b></em>", "<em><b>Near</b></em>");
        t("rtp.nah.lore", "<mu>Nicht weit von der Stadt – gut für den Anfang</mu>", "<mu>Not far from town – good to start</mu>");
        t("rtp.mittel", "<au><b>Mittel</b></au>", "<au><b>Medium</b></au>");
        t("rtp.mittel.lore", "<mu>Mehr Platz, weniger Nachbarn</mu>", "<mu>More room, fewer neighbours</mu>");
        t("rtp.weit", "<bl><b>Weit</b></bl>", "<bl><b>Far</b></bl>");
        t("rtp.weit.lore", "<mu>Unberührte Gegend nahe am Rand der Welt</mu>", "<mu>Untouched land near the edge of the world</mu>");
        t("rtp.nether", "<red><b>Nether</b></red>", "<red><b>Nether</b></red>");
        t("rtp.nether.lore", "<mu>Ein sicherer Platz im Nether</mu>", "<mu>A safe spot in the Nether</mu>");
        t("rtp.end", "<light_purple><b>End</b></light_purple>", "<light_purple><b>End</b></light_purple>");
        t("rtp.end.lore", "<mu>Auf eine der äußeren Inseln</mu>", "<mu>To one of the outer islands</mu>");
        t("rtp.range", "<white><min> – <max> Blöcke von der Stadt</white>", "<white><min> – <max> blocks from town</white>");
        t("rtp.click", "<em>Klick: los geht's</em>", "<em>Click: let's go</em>");
        t("rtp.info", "<white>Zufalls-Teleport</white>", "<white>Random teleport</white>");
        t("rtp.ready.lore", "<em>Bereit!</em>", "<em>Ready!</em>");
        t("rtp.cooldown.lore", "<mu>Wieder möglich in <time></mu>", "<mu>Available again in <time></mu>");
        t("rtp.cooldown", "<red>Noch kurz warten – wieder möglich in <time>.</red>", "<red>Please wait – available again in <time>.</red>");
        t("rtp.unknown", "So geht's: <au>/rtp</au> oder <au>/rtp nah|mittel|weit</au>",
                "Usage: <au>/rtp</au> or <au>/rtp near|medium|far</au>");
        t("rtp.not-yet", "<red>Dorthin geht es erst, wenn du selbst schon einmal dort warst.</red>",
                "<red>You can only go there after you've been there yourself.</red>");
        t("rtp.busy", "<red>Ich suche schon einen Platz für dich …</red>", "<red>Already looking for a spot for you …</red>");
        t("rtp.searching", "Ich suche einen sicheren Platz …", "Looking for a safe spot …");
        t("rtp.failed", "<red>Diesmal habe ich keinen sicheren Platz gefunden – versuch es nochmal.</red>",
                "<red>Couldn't find a safe spot this time – please try again.</red>");
        t("rtp.arrived", "Du bist bei <white><x> / <z></white>. Viel Glück! <mu>Sichere dein Zuhause mit /gs sichern "
                + "und /sethome.</mu>",
                "You're at <white><x> / <z></white>. Good luck! <mu>Protect your home with /gs sichern and /sethome.</mu>");

        // Schutz
        t("protect.town", "<red>Die Marktstadt ist geschützt.</red>", "<red>The market town is protected.</red>");
        t("protect.claim", "<red>Das Grundstück gehört <owner>.</red>", "<red>This land belongs to <owner>.</red>");
        t("protect.pvp", "<red>Hier ist Kämpfen aus.</red>", "<red>Fighting is off here.</red>");

        // Grundstücke
        t("claim.help.title", "<au><b>Grundstücke – /gs</b></au>", "<au><b>Land claims – /gs</b></au>");
        t("claim.help.sichern", " <au>/gs sichern</au> <mu>– den Chunk sichern, auf dem du stehst (16 × 16, ganze Höhe)</mu>",
                " <au>/gs sichern</au> <mu>– claim the chunk you stand on (16 × 16, full height)</mu>");
        t("claim.help.aufgeben", " <au>/gs aufgeben</au> <mu>– Chunk freigeben (kein Geld zurück)</mu>",
                " <au>/gs aufgeben</au> <mu>– release the chunk (no refund)</mu>");
        t("claim.help.info", " <au>/gs info</au> <mu>– wem gehört das hier?</mu>", " <au>/gs info</au> <mu>– who owns this?</mu>");
        t("claim.help.liste", " <au>/gs liste</au> <mu>– alle deine Grundstücke</mu>", " <au>/gs liste</au> <mu>– all your claims</mu>");
        t("claim.help.vertrauen", " <au>/gs vertrauen \\<spieler></au> <mu>– darf überall bei dir bauen</mu>",
                " <au>/gs vertrauen \\<player></au> <mu>– may build on all your land</mu>");
        t("claim.help.misstrauen", " <au>/gs misstrauen \\<spieler></au> <mu>– Vertrauen zurücknehmen</mu>",
                " <au>/gs misstrauen \\<player></au> <mu>– remove trust</mu>");
        t("claim.help.pvp", " <au>/gs pvp an|aus</au> <mu>– Kämpfen auf diesem Chunk erlauben</mu>",
                " <au>/gs pvp an|aus</au> <mu>– allow fighting on this chunk</mu>");
        t("claim.help.grenzen", " <au>/gs grenzen</au> <mu>– Ränder 10 Sekunden lang zeigen</mu>",
                " <au>/gs grenzen</au> <mu>– show borders for 10 seconds</mu>");
        t("claim.help.cost", "<mu>Die ersten <free> Chunks sind gratis, jeder weitere kostet <price>, höchstens <max>.</mu>",
                "<mu>The first <free> chunks are free, each further one costs <price>, <max> at most.</mu>");
        t("claim.no-end", "<red>Im End gibt es keine Grundstücke.</red>", "<red>There are no claims in the End.</red>");
        t("claim.too-close", "<red>So nah an der Marktstadt geht das nicht (mindestens <chunks> Chunks Abstand).</red>",
                "<red>Too close to the market town (keep at least <chunks> chunks away).</red>");
        t("claim.done.free", "<em>✔</em> Gesichert! Das ist dein Grundstück Nr. <count> <mu>(<free> sind gratis)</mu>.",
                "<em>✔</em> Claimed! This is your claim no. <count> <mu>(<free> are free)</mu>.");
        t("claim.done.paid", "<em>✔</em> Gesichert für <au><price></au> – dein Grundstück Nr. <count>.",
                "<em>✔</em> Claimed for <au><price></au> – your claim no. <count>.");
        t("claim.own", "Dieser Chunk gehört dir schon.", "This chunk is already yours.");
        t("claim.taken", "<red>Dieser Chunk gehört schon <owner>.</red>", "<red>This chunk already belongs to <owner>.</red>");
        t("claim.limit", "<red>Du hast schon <max> Grundstücke – mehr gehen nicht.</red>",
                "<red>You already have <max> claims – that's the limit.</red>");
        t("claim.none-here", "<red>Hier ist kein Grundstück.</red>", "<red>There's no claim here.</red>");
        t("claim.not-yours", "<red>Das Grundstück gehört <owner>.</red>", "<red>This claim belongs to <owner>.</red>");
        t("claim.released", "Grundstück freigegeben <mu>(<shops> Läden geschlossen)</mu>.",
                "Claim released <mu>(<shops> shops closed)</mu>.");
        t("claim.info.town", "Hier ist die <bl>Marktstadt</bl> – sie gehört allen und ist geschützt.",
                "This is the <bl>market town</bl> – it belongs to everyone and is protected.");
        t("claim.info.free", "Chunk <white><x> / <z></white> ist frei.", "Chunk <white><x> / <z></white> is free.");
        t("claim.info.hint", "<mu>Mit <au>/gs sichern</au> gehört er dir.</mu>", "<mu><au>/gs sichern</au> makes it yours.</mu>");
        t("claim.info.owned", "Dieser Chunk gehört <white><owner></white> <mu>(Kämpfen: <pvp>)</mu>.",
                "This chunk belongs to <white><owner></white> <mu>(fighting: <pvp>)</mu>.");
        t("claim.info.trusted", "<mu>Vertraut: <names></mu>", "<mu>Trusted: <names></mu>");
        t("claim.info.count", "<mu>Deine Grundstücke: <count> (gratis: <free>, höchstens <max>)</mu>",
                "<mu>Your claims: <count> (free: <free>, at most <max>)</mu>");
        t("claim.list.empty", "Du hast noch kein Grundstück.", "You don't have any claims yet.");
        t("claim.list.title", "Deine Grundstücke (<count>):", "Your claims (<count>):");
        t("claim.list.line", " <em>▸</em> <white><world></white> <mu>bei</mu> <white><x> / <z></white> <mu>(Kämpfen: <pvp>)</mu>",
                " <em>▸</em> <white><world></white> <mu>at</mu> <white><x> / <z></white> <mu>(fighting: <pvp>)</mu>");
        t("claim.trust.usage", "So geht's: <au>/gs vertrauen \\<spieler></au>", "Usage: <au>/gs vertrauen \\<player></au>");
        t("claim.untrust.usage", "So geht's: <au>/gs misstrauen \\<spieler></au>", "Usage: <au>/gs misstrauen \\<player></au>");
        t("claim.trust.self", "<red>Dir selbst vertraust du sowieso.</red>", "<red>You trust yourself anyway.</red>");
        t("claim.trusted", "<em>✔</em> <white><name></white> darf jetzt auf all deinen Grundstücken bauen.",
                "<em>✔</em> <white><name></white> may now build on all your land.");
        t("claim.trusted.you", "<white><name></white> vertraut dir – du darfst auf den Grundstücken bauen.",
                "<white><name></white> trusts you – you may build on their land.");
        t("claim.untrusted", "<white><name></white> darf nicht mehr bei dir bauen.", "<white><name></white> may no longer build on your land.");
        t("claim.untrust.unknown", "<red><name> steht nicht auf deiner Liste.</red>", "<red><name> is not on your list.</red>");
        t("claim.pvp.not-yours", "<red>Stell dich auf dein eigenes Grundstück.</red>", "<red>Stand on your own claim.</red>");
        t("claim.pvp.on", "<red>⚔</red> Kämpfen ist auf diesem Chunk jetzt <red>an</red>.",
                "<red>⚔</red> Fighting on this chunk is now <red>on</red>.");
        t("claim.pvp.off", "Kämpfen ist auf diesem Chunk jetzt <em>aus</em>.", "Fighting on this chunk is now <em>off</em>.");
    }
}
