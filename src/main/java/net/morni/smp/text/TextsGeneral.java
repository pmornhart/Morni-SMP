package net.morni.smp.text;

/** Texte – allgemein: Geld, Menüs, Teleports, Zuhause, Kampf, Kopfgeld, Belohnung, Leiste, Hilfe, Verwaltung. */
final class TextsGeneral {

    private TextsGeneral() {
    }

    private static void t(String key, String de, String en) {
        Texts.t(key, de, en);
    }

    static void register() {
        t("prefix", "<au><b>MorniSMP</b></au> <mu>»</mu> ", "<au><b>MorniSMP</b></au> <mu>»</mu> ");
        t("word.on", "an", "on");
        t("word.off", "aus", "off");

        // Befehle allgemein
        t("command.players-only", "<red>Das geht nur im Spiel.</red>", "<red>This only works in game.</red>");
        t("command.error", "<red>Ups, da ist etwas schiefgegangen. Das Team schaut es sich an.</red>",
                "<red>Oops, something went wrong. The team will have a look.</red>");
        t("command.no-permission", "<red>Das darf nur das Team.</red>", "<red>Only the team can do that.</red>");
        t("player.unknown", "<red><name> war hier noch nie.</red>", "<red><name> has never been here.</red>");
        t("player.offline", "<red><name> ist gerade nicht online.</red>", "<red><name> is not online right now.</red>");

        // Menüs
        t("menu.back", "<gray>← Zurück</gray>", "<gray>← Back</gray>");
        t("menu.close", "<red>Schließen</red>", "<red>Close</red>");
        t("menu.next", "<em>Weiter →</em>", "<em>Next →</em>");
        t("menu.prev", "<em>← Vorherige Seite</em>", "<em>← Previous page</em>");
        t("menu.refresh", "<bl>Aktualisieren</bl>", "<bl>Refresh</bl>");
        t("menu.cancelled", "<mu>Abgebrochen.</mu>", "<mu>Cancelled.</mu>");
        t("confirm.no", "<red>Nein, lieber nicht</red>", "<red>No, never mind</red>");
        t("line.balance", "<mu>Dein Kontostand:</mu> <au><balance></au>", "<mu>Your balance:</mu> <au><balance></au>");

        // Zahlen wählen
        t("picker.minus", "<red>− <step></red>", "<red>− <step></red>");
        t("picker.plus", "<em>+ <step></em>", "<em>+ <step></em>");
        t("picker.value", "<white>Gewählt:</white> <au><value></au>", "<white>Selected:</white> <au><value></au>");
        t("picker.type", "<au>Selbst eintippen</au>", "<au>Type it yourself</au>");
        t("picker.type.lore", "<mu>Klick, dann die Zahl in den Chat schreiben</mu>",
                "<mu>Click, then write the number in chat</mu>");
        t("picker.ask.money", "Schreib den Preis in den Chat (z. B. <au>250</au>, <au>12,50</au> oder <au>1,5k</au>) – "
                + "„<mu>abbrechen</mu>“ bricht ab.",
                "Write the price in chat (e.g. <au>250</au>, <au>12.50</au> or <au>1.5k</au>) – “<mu>cancel</mu>” stops.");
        t("picker.ask.amount", "Schreib die Menge in den Chat – „<mu>abbrechen</mu>“ bricht ab.",
                "Write the amount in chat – “<mu>cancel</mu>” stops.");
        t("picker.invalid", "<red>Die Zahl passt nicht – probier es nochmal.</red>",
                "<red>That number doesn't fit – try again.</red>");
        t("picker.ok", "<em><b>✔ Passt: <value></b></em>", "<em><b>✔ Confirm: <value></b></em>");

        // Suche + Abteilungen (Auktionshaus und Aufträge)
        t("search.button", "<au>Suchen</au>", "<au>Search</au>");
        t("search.lore", "<mu>Klick und einen Namen schreiben, z. B. „diamant“ oder „schwert“</mu>",
                "<mu>Click and write a name, e.g. “diamond” or “sword”</mu>");
        t("search.current", "<white>Suche:</white> <au><text></au>", "<white>Search:</white> <au><text></au>");
        t("search.clear", "<mu>Rechtsklick: Suche löschen</mu>", "<mu>Right-click: clear search</mu>");
        t("search.ask", "Wonach suchst du? Schreib es in den Chat (Deutsch oder Englisch) – „<mu>abbrechen</mu>“ bricht ab.",
                "What are you looking for? Write it in chat (English or German) – “<mu>cancel</mu>” stops.");
        t("cat.all", "<white>Alles</white>", "<white>Everything</white>");
        t("cat.bloecke", "<white>Blöcke</white>", "<white>Blocks</white>");
        t("cat.werkzeug", "<white>Werkzeug & Waffen</white>", "<white>Tools & weapons</white>");
        t("cat.ruestung", "<white>Rüstung</white>", "<white>Armour</white>");
        t("cat.essen", "<white>Essen & Ernte</white>", "<white>Food & crops</white>");
        t("cat.redstone", "<white>Redstone</white>", "<white>Redstone</white>");
        t("cat.traenke", "<white>Tränke & Zauber</white>", "<white>Potions & enchantments</white>");
        t("cat.sonstiges", "<white>Sonstiges</white>", "<white>Other</white>");

        // Geld
        t("money.balance", "Dein Kontostand: <au><balance></au>", "Your balance: <au><balance></au>");
        t("money.balance.other", "Kontostand von <white><name></white>: <au><balance></au>",
                "<white><name></white>'s balance: <au><balance></au>");
        t("money.invalid", "<red>Der Betrag passt nicht. Schreib z. B. <white>250</white>, <white>12,50</white> oder "
                + "<white>1,5k</white>.</red>",
                "<red>That amount doesn't work. Write e.g. <white>250</white>, <white>12.50</white> or <white>1.5k</white>."
                        + "</red>");
        t("money.not-enough", "<red>Dafür reicht dein Geld nicht – es fehlen <white><missing></white>.</red>",
                "<red>You can't afford that – you're <white><missing></white> short.</red>");
        t("pay.usage", "So geht's: <au>/zahlen \\<spieler> \\<betrag></au>", "Usage: <au>/pay \\<player> \\<amount></au>");
        t("pay.self", "<red>An dich selbst überweisen geht nicht.</red>", "<red>You can't pay yourself.</red>");
        t("pay.confirm.title", "Wirklich überweisen?", "Really send this?");
        t("pay.confirm.to", "<white>An <name></white>", "<white>To <name></white>");
        t("pay.confirm.amount", "<au><amount></au>", "<au><amount></au>");
        t("pay.confirm.yes", "<em><b>Ja, <amount> überweisen</b></em>", "<em><b>Yes, send <amount></b></em>");
        t("pay.sent", "<em>✔</em> Du hast <au><amount></au> an <white><name></white> überwiesen. Neuer Stand: "
                + "<au><balance></au>",
                "<em>✔</em> You sent <au><amount></au> to <white><name></white>. New balance: <au><balance></au>");
        t("pay.received", "<em>✔</em> <white><name></white> hat dir <au><amount></au> überwiesen.",
                "<em>✔</em> <white><name></white> sent you <au><amount></au>.");
        t("notice.pay.received", "<em>✔</em> <white><name></white> hat dir <au><paid></au> überwiesen, während du weg "
                + "warst.", "<em>✔</em> <white><name></white> sent you <au><paid></au> while you were away.");
        t("rich.title", "<au><b>Die Reichsten</b></au>", "<au><b>The richest</b></au>");
        t("rich.line", "<au><rank>.</au> <white><name></white> <mu>–</mu> <au><balance></au>",
                "<au><rank>.</au> <white><name></white> <mu>–</mu> <au><balance></au>");
        t("rich.empty", "<mu>Noch niemand.</mu>", "<mu>Nobody yet.</mu>");

        // Willkommen
        t("welcome.title", "<au><b>MorniSMP</b></au>", "<au><b>MorniSMP</b></au>");
        t("welcome.subtitle", "<white>Willkommen in der Marktstadt!</white>", "<white>Welcome to the market town!</white>");
        t("welcome.first", "Schön, dass du da bist! Zum Start bekommst du <au><money></au>. Handle auf dem Markt, "
                + "sichere dir ein Grundstück und baue dir etwas auf.",
                "Great to have you! To start, you get <au><money></au>. Trade on the market, claim some land and build "
                        + "something.");
        t("welcome.help", "<mu>Tipp:</mu> <au>/smp</au> zeigt alle Befehle · Die drei <bl>Reisetore</bl> in der Stadtmauer "
                + "bringen dich in die Wildnis: <green>West = Oberwelt</green>, <red>Nord = Nether</red>, "
                + "<light_purple>Ost = End</light_purple>.",
                "<mu>Tip:</mu> <au>/smp</au> lists all commands · The three <bl>travel gates</bl> in the town wall take "
                        + "you into the wild: <green>west = Overworld</green>, <red>north = Nether</red>, "
                        + "<light_purple>east = End</light_purple>.");
        t("welcome.back", "Willkommen zurück! Kontostand: <au><balance></au>",
                "Welcome back! Balance: <au><balance></au>");

        // Leiste
        t("sidebar.title", "<au><b>MorniSMP</b></au>", "<au><b>MorniSMP</b></au>");
        t("sidebar.balance", "<mu>●</mu> Geld: <au><money></au>", "<mu>●</mu> Money: <au><money></au>");
        t("sidebar.place", "<mu>●</mu> <place>", "<mu>●</mu> <place>");
        t("sidebar.place.town", "<bl>Marktstadt</bl>", "<bl>Market town</bl>");
        t("sidebar.place.own", "<em>Dein Grundstück</em>", "<em>Your land</em>");
        t("sidebar.place.claim", "<white>Grundstück: <owner></white>", "<white>Land: <owner></white>");
        t("sidebar.place.wild", "<green>Wildnis</green>", "<green>Wilderness</green>");
        t("sidebar.place.nether", "<red>Nether</red>", "<red>Nether</red>");
        t("sidebar.place.end", "<light_purple>End</light_purple>", "<light_purple>End</light_purple>");
        t("sidebar.bounty", "<mu>●</mu> <red>Kopfgeld: <money></red>", "<mu>●</mu> <red>Bounty: <money></red>");
        t("sidebar.combat", "<mu>●</mu> <red><b>Im Kampf: <seconds> s</b></red>", "<mu>●</mu> <red><b>In combat: <seconds> s</b></red>");
        t("sidebar.mail", "<mu>●</mu> <au>Abholfach: <count></au>", "<mu>●</mu> <au>Mailbox: <count></au>");
        t("sidebar.online", "<mu>●</mu> Online: <white><count></white>", "<mu>●</mu> Online: <white><count></white>");
        t("sidebar.address", "<mu><address></mu>", "<mu><address></mu>");
        t("sidebar.on", "Leiste ist an.", "Sidebar is on.");
        t("sidebar.off", "Leiste ist aus. <mu>(/leiste schaltet sie wieder an)</mu>",
                "Sidebar is off. <mu>(/sidebar turns it back on)</mu>");

        // Kampf
        t("combat.tagged", "<red>⚔ Du bist im Kampf!</red> <mu><seconds> Sekunden lang kein Teleport – wer jetzt "
                + "geht, verliert seine Sachen.</mu>",
                "<red>⚔ You are in combat!</red> <mu>No teleports for <seconds> seconds – leaving now drops your "
                        + "items.</mu>");
        t("combat.bar", "<red>⚔ Im Kampf: <seconds> s</red>", "<red>⚔ In combat: <seconds> s</red>");
        t("combat.over", "<em>✔ Kampf vorbei</em>", "<em>✔ Combat over</em>");
        t("combat.blocked", "<red>Im Kampf geht das nicht – noch <seconds> Sekunden.</red>",
                "<red>Not during combat – <seconds> more seconds.</red>");
        t("combat.logout", "<red><name> ist mitten im Kampf geflüchtet und hat alles fallen lassen.</red>",
                "<red><name> fled in the middle of a fight and dropped everything.</red>");

        // Teleport
        t("teleport.bar", "Teleport <place> in <seconds> s – nicht bewegen!",
                "Teleporting <place> in <seconds> s – don't move!");
        t("teleport.dont-move", "<au>Stillhalten …</au>", "<au>Hold still …</au>");
        t("teleport.done", "<em>✔ Angekommen <place></em>", "<em>✔ Arrived <place></em>");
        t("teleport.failed", "<red>Der Teleport hat nicht geklappt – probier es gleich nochmal.</red>",
                "<red>The teleport didn't work – try again in a moment.</red>");
        t("teleport.nowhere", "<red>Dieses Ziel gibt es gerade nicht.</red>", "<red>That destination doesn't exist right now.</red>");
        t("teleport.cancel.moved", "<red>Teleport abgebrochen – du hast dich bewegt.</red>",
                "<red>Teleport cancelled – you moved.</red>");
        t("teleport.cancel.damage", "<red>Teleport abgebrochen – du hast Schaden bekommen.</red>",
                "<red>Teleport cancelled – you took damage.</red>");
        t("teleport.cancel.combat", "<red>Teleport abgebrochen – du bist im Kampf.</red>",
                "<red>Teleport cancelled – you are in combat.</red>");
        t("place.spawn", "<bl>in die Marktstadt</bl>", "<bl>to the market town</bl>");
        t("place.home", "<em>nach Hause</em>", "<em>home</em>");
        t("place.back", "<au>zurück</au>", "<au>back</au>");
        t("place.wild", "<green>in die Wildnis</green>", "<green>into the wild</green>");
        t("place.player", "<white>zum Spieler</white>", "<white>to the player</white>");
        t("back.none", "<red>Es gibt keinen Ort, an den du zurück kannst.</red>", "<red>There's no place to go back to.</red>");

        // Zuhause
        t("home.set", "<em>✔</em> Zuhause <white><name></white> gesetzt.", "<em>✔</em> Home <white><name></white> set.");
        t("home.moved", "<em>✔</em> Zuhause <white><name></white> ist jetzt hier.",
                "<em>✔</em> Home <white><name></white> moved here.");
        t("home.limit", "<red>Du hast schon <max> Zuhause. Lösch eins mit /delhome \\<name>.</red>",
                "<red>You already have <max> homes. Delete one with /delhome \\<name>.</red>");
        t("home.bad-name", "<red>Der Name darf nur Buchstaben, Ziffern, _ und - haben (höchstens 16).</red>",
                "<red>Names may only use letters, digits, _ and - (16 at most).</red>");
        t("home.none", "Du hast noch kein Zuhause. Stell dich hin und tippe <au>/sethome</au>.",
                "You have no home yet. Stand somewhere and type <au>/sethome</au>.");
        t("home.unknown", "<red>Ein Zuhause „<name>“ gibt es nicht. <mu>/homes zeigt alle.</mu></red>",
                "<red>There's no home called “<name>”. <mu>/homes lists them.</mu></red>");
        t("home.world-gone", "<red>Die Welt dieses Zuhauses gibt es nicht mehr.</red>",
                "<red>The world of this home no longer exists.</red>");
        t("home.not-in-town", "<red>In der Spawn-Welt kannst du kein Zuhause setzen – /spawn bringt dich hierher.</red>",
                "<red>You can't set a home in the spawn world – /spawn brings you here.</red>");
        t("home.menu.title", "Deine Zuhause", "Your homes");
        t("home.menu.where", "<mu><world> · <x> <y> <z></mu>", "<mu><world> · <x> <y> <z></mu>");
        t("home.menu.click", "<em>Klick: hin</em>", "<em>Click: go there</em>");
        t("home.del.usage", "So geht's: <au>/delhome \\<name></au>", "Usage: <au>/delhome \\<name></au>");
        t("home.deleted", "Zuhause <white><name></white> gelöscht.", "Home <white><name></white> deleted.");
        t("home.list", "Deine Zuhause (<count>/<max>) – zum Hinreisen anklicken:",
                "Your homes (<count>/<max>) – click to travel:");
        t("home.list.line", " <em>▸</em> <white><name></white> <mu>(<world>)</mu>",
                " <em>▸</em> <white><name></white> <mu>(<world>)</mu>");

        // Teleport-Anfragen
        t("tpa.usage", "So geht's: <au>/tpa \\<spieler></au>", "Usage: <au>/tpa \\<player></au>");
        t("tpa.usage.here", "So geht's: <au>/tpahere \\<spieler></au>", "Usage: <au>/tpahere \\<player></au>");
        t("tpa.self", "<red>Das bist du selbst.</red>", "<red>That's you.</red>");
        t("tpa.pending", "<red>Du hast <name> schon gefragt – warte auf die Antwort.</red>",
                "<red>You already asked <name> – wait for the answer.</red>");
        t("tpa.sent", "Anfrage an <white><name></white> geschickt (1 Minute gültig).",
                "Request sent to <white><name></white> (valid for 1 minute).");
        t("tpa.incoming", "<white><name></white> möchte sich zu dir teleportieren.",
                "<white><name></white> wants to teleport to you.");
        t("tpa.incoming.here", "<white><name></white> möchte, dass du zu ihm/ihr kommst.",
                "<white><name></white> wants you to come to them.");
        t("tpa.buttons", "<mu>Antwort:</mu>", "<mu>Answer:</mu>");
        t("tpa.accept.button", "<em><b>[Annehmen]</b></em>", "<em><b>[Accept]</b></em>");
        t("tpa.deny.button", "<red><b>[Ablehnen]</b></red>", "<red><b>[Deny]</b></red>");
        t("tpa.none", "<red>Du hast keine offene Anfrage.</red>", "<red>You have no open request.</red>");
        t("tpa.accepted", "<em>✔</em> <white><name></white> hat angenommen.", "<em>✔</em> <white><name></white> accepted.");
        t("tpa.accepted.you", "Du hast die Anfrage von <white><name></white> angenommen.",
                "You accepted <white><name></white>'s request.");
        t("tpa.denied", "<red><name> hat abgelehnt.</red>", "<red><name> declined.</red>");
        t("tpa.denied.you", "Du hast die Anfrage von <white><name></white> abgelehnt.",
                "You declined <white><name></white>'s request.");

        // Kopfgeld
        t("bounty.title", "<red><b>Kopfgelder</b></red>", "<red><b>Bounties</b></red>");
        t("bounty.line", " <red>☠</red> <white><name></white> <mu>–</mu> <au><amount></au>",
                " <red>☠</red> <white><name></white> <mu>–</mu> <au><amount></au>");
        t("bounty.empty", "<mu>Gerade ist auf niemanden ein Kopfgeld ausgesetzt.</mu>",
                "<mu>There are no bounties right now.</mu>");
        t("bounty.usage", "<mu>Aussetzen: /kopfgeld \\<spieler> \\<betrag> (mindestens <min>). Wer das Ziel im Kampf "
                + "besiegt, bekommt alles.</mu>",
                "<mu>Place one: /bounty \\<player> \\<amount> (at least <min>). Whoever defeats the target in a fight gets "
                        + "it all.</mu>");
        t("bounty.placed", "<red>☠</red> <white><name></white> setzt <au><amount></au> Kopfgeld auf "
                + "<white><target></white> aus (jetzt <au><total></au>).",
                "<red>☠</red> <white><name></white> put a <au><amount></au> bounty on <white><target></white> "
                        + "(now <au><total></au>).");
        t("bounty.self", "<red>Auf dich selbst geht das nicht.</red>", "<red>You can't put a bounty on yourself.</red>");
        t("bounty.too-low", "<red>Ein Kopfgeld muss mindestens <min> sein.</red>", "<red>A bounty must be at least <min>.</red>");
        t("bounty.claimed", "<red>☠</red> <white><killer></white> hat <white><target></white> besiegt und "
                + "<au><amount></au> Kopfgeld kassiert!",
                "<red>☠</red> <white><killer></white> defeated <white><target></white> and collected a "
                        + "<au><amount></au> bounty!");

        // Belohnung
        t("reward.ok", "<em>✔</em> Tägliche Belohnung: <au><money></au> – Tag <au><streak></au> von <max> in Folge!",
                "<em>✔</em> Daily reward: <au><money></au> – day <au><streak></au> of <max> in a row!");
        t("reward.already", "Heute hast du deine Belohnung schon (Serie: <au><streak></au>). Bis morgen!",
                "You already got today's reward (streak: <au><streak></au>). See you tomorrow!");
        t("reward.too-early", "Die Belohnung gibt es, wenn du heute <minutes> Minuten gespielt hast – noch <au><time></au>.",
                "The reward unlocks after <minutes> minutes of play today – <au><time></au> to go.");

        // Hilfe
        t("help.title", "<au><b>MorniSMP – Befehle</b></au>", "<au><b>MorniSMP – commands</b></au>");
        t("help.money", " <au>/geld</au> · <au>/zahlen \\<spieler> \\<betrag></au> · <au>/reichste</au>",
                " <au>/money</au> · <au>/pay \\<player> \\<amount></au> · <au>/baltop</au>");
        t("help.market", " <au>/ah</au> Auktionshaus · <au>/ah verkaufen \\<preis></au> (Hand) · <au>/abholen</au> Abholfach",
                " <au>/ah</au> auction house · <au>/ah sell \\<price></au> (hand) · <au>/collect</au> mailbox");
        t("help.orders", " <au>/auftraege</au> Kaufaufträge ansehen, beliefern, aufgeben",
                " <au>/orders</au> view, fill and place buy orders");
        t("help.buyback", " <au>/verkaufen</au> Sachen hineinlegen und an die Händlerin verkaufen",
                " <au>/sell</au> drop items in and sell them to the merchant");
        t("help.stall", " <au>/stand mieten</au> Marktstand am Marktplatz mieten (vor dem Stand stehen)",
                " <au>/stall rent</au> rent a market stall (stand in front of it)");
        t("help.shop", " <mu>Laden:</mu> Kiste + Schild mit <au>[Laden]</au> in der ersten Zeile (eigenes Grundstück "
                + "oder Stand)",
                " <mu>Shop:</mu> chest + sign with <au>[Shop]</au> on the first line (own land or stall)");
        t("help.claims", " <au>/gs sichern</au> Chunk sichern · <au>/gs</au> Info · <au>/gs vertrauen \\<spieler></au>",
                " <au>/gs sichern</au> claim chunk · <au>/gs</au> info · <au>/gs vertrauen \\<player></au> trust");
        t("help.travel", " <au>/spawn</au> Marktstadt · <au>/rtp</au> Zufalls-Teleport (Oberwelt, Nether, End) · <au>/back</au> zurück",
                " <au>/spawn</au> market town · <au>/rtp</au> random teleport (Overworld, Nether, End) · <au>/back</au> go back");
        t("help.homes", " <au>/sethome [name]</au> · <au>/home [name]</au> · <au>/homes</au> · <au>/delhome \\<name></au>",
                " <au>/sethome [name]</au> · <au>/home [name]</au> · <au>/homes</au> · <au>/delhome \\<name></au>");
        t("help.tpa", " <au>/tpa \\<spieler></au> · <au>/tpahere \\<spieler></au> · <au>/tpaccept</au> · <au>/tpdeny</au>",
                " <au>/tpa \\<player></au> · <au>/tpahere \\<player></au> · <au>/tpaccept</au> · <au>/tpdeny</au>");
        t("help.bounty", " <au>/kopfgeld</au> Kopfgelder ansehen oder aussetzen", " <au>/bounty</au> view or place bounties");
        t("help.reward", " <au>/belohnung</au> tägliche Belohnung (jeden Tag mehr)",
                " <au>/daily</au> daily reward (more every day in a row)");
        t("help.sidebar", " <au>/leiste</au> Leiste rechts an/aus", " <au>/sidebar</au> sidebar on/off");

        // Verwaltung
        t("admin.help.title", "<au><b>/smpadmin</b></au>", "<au><b>/smpadmin</b></au>");
        t("admin.help.geld", " <au>geld geben|nehmen|setzen \\<spieler> \\<betrag></au>",
                " <au>geld geben|nehmen|setzen \\<player> \\<amount></au> (give|take|set)");
        t("admin.help.bau", " <au>bau</au> – Bau-Modus (Stadt und Grundstücke bearbeiten)",
                " <au>bau</au> – build mode (edit town and claims)");
        t("admin.help.stadt", " <au>stadt [neu|tore]</au> – Stand der Marktstadt / neu bauen / Reisetore",
                " <au>stadt [neu|tore]</au> – market town status / rebuild / travel gates");
        t("admin.help.neuladen", " <au>neuladen</au> – config.yml neu lesen", " <au>neuladen</au> – reload config.yml");
        t("admin.help.stand", " <au>stand frei \\<nummer></au> – Stand räumen (Sachen ins Abholfach)",
                " <au>stand frei \\<number></au> – clear a stall (items to the mailbox)");
        t("admin.help.gs", " <au>gs</au> – Grundstück hier entfernen", " <au>gs</au> – remove the claim here");
        t("admin.help.info", " <au>info</au> – Kennzahlen", " <au>info</au> – key figures");
        t("admin.reloaded", "<em>✔</em> Einstellungen neu geladen.", "<em>✔</em> Settings reloaded.");
        t("admin.money.usage", "So geht's: <au>/smpadmin geld geben|nehmen|setzen \\<spieler> \\<betrag></au>",
                "Usage: <au>/smpadmin geld geben|nehmen|setzen \\<player> \\<amount></au>");
        t("admin.money.done", "<em>✔</em> <white><name></white> hat jetzt <au><balance></au>.",
                "<em>✔</em> <white><name></white> now has <au><balance></au>.");
        t("admin.money.not-enough", "<red><name> hat nicht so viel.</red>", "<red><name> doesn't have that much.</red>");
        t("admin.build.on", "<au>Bau-Modus an</au> – du kannst Stadt und Grundstücke bearbeiten. Schleichen + Klick "
                + "benutzt Schalter normal.",
                "<au>Build mode on</au> – you can edit town and claims. Sneak + click uses counters normally.");
        t("admin.build.off", "Bau-Modus aus.", "Build mode off.");
        t("admin.town.rebuilding", "Die Marktstadt wird neu gebaut …", "Rebuilding the market town …");
        t("admin.town.busy", "<red>Es wird gerade schon gebaut.</red>", "<red>A build is already running.</red>");
        t("admin.town.gate", " <au><target></au>: Tor <white><gate></white>, davor <white><back></white>",
                " <au><target></au>: gate <white><gate></white>, in front <white><back></white>");
        t("admin.town.info", "Marktstadt in <white><world></white>: fertig <white><ready></white>, Boden y=<white><y></white>, "
                + "<white><stalls></white> Stände, Ankunft <white><spawn></white>",
                "Market town in <white><world></white>: ready <white><ready></white>, ground y=<white><y></white>, <white><stalls></white> "
                        + "stalls, arrival <white><spawn></white>");
        t("admin.stall.usage", "So geht's: <au>/smpadmin stand frei \\<nummer></au>",
                "Usage: <au>/smpadmin stand frei \\<number></au>");
        t("admin.stall.freed", "<em>✔</em> Stand <id> geräumt (<count> Einträge ins Abholfach).",
                "<em>✔</em> Stall <id> cleared (<count> entries to the mailbox).");
        t("admin.claim.removed", "<em>✔</em> Grundstück von <white><owner></white> entfernt (<shops> Läden geschlossen).",
                "<em>✔</em> Removed <white><owner></white>'s claim (<shops> shops closed).");
        t("admin.info", "Geld bei Spielern: <au><total></au> · in Treuhand: <au><escrow></au> · Angebote: "
                + "<white><listings></white> · Aufträge: <white><orders></white> · Netzwerk: <white><network></white>",
                "Money held by players: <au><total></au> · in escrow: <au><escrow></au> · listings: "
                        + "<white><listings></white> · orders: <white><orders></white> · network: <white><network></white>");
    }
}
