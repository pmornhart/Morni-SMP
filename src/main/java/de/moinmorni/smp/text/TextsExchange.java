package de.moinmorni.smp.text;

/** Texte – Börse ({@code /boerse}), Wert ({@code /wert}) und Sofortverkauf ({@code /verkaufen hand|alles}). */
final class TextsExchange {

    private TextsExchange() {
    }

    private static void t(String key, String de, String en) {
        Texts.t(key, de, en);
    }

    static void register() {
        menu();
        detail();
        worth();
        quickSell();
        admin();
    }

    private static void menu() {
        t("exchange.title", "Börse der Marktstadt", "Market town exchange");
        t("exchange.title.list", "Börse: <what> <dark_gray>(<page>/<pages>)</dark_gray>",
                "Exchange: <what> <dark_gray>(<page>/<pages>)</dark_gray>");
        t("exchange.title.cats", "Börse: Abteilungen", "Exchange: departments");
        t("exchange.head", "<au><b>Börse</b></au>", "<au><b>Exchange</b></au>");
        t("exchange.head.l1", "<white>Vronis Preise folgen Angebot und Nachfrage:</white>",
                "<white>Vroni's prices follow supply and demand:</white>");
        t("exchange.head.l2", "<mu>Verkauft die Stadt viel, zahlt sie weniger.</mu>",
                "<mu>If the town sells a lot, she pays less.</mu>");
        t("exchange.head.l3", "<mu>Kauft die Stadt viel, verlangt sie mehr.</mu>",
                "<mu>If the town buys a lot, she charges more.</mu>");
        t("exchange.head.l4", "<mu>Mit der Zeit geht jeder Kurs zurück zum Grundpreis.</mu>",
                "<mu>Over time every price returns to its base.</mu>");
        t("exchange.up", "<green><b>▲ Steiger</b></green>", "<green><b>▲ Rising</b></green>");
        t("exchange.up.lore", "<mu>Gerade gefragt – Vroni verlangt mehr</mu>", "<mu>In demand – Vroni charges more</mu>");
        t("exchange.down", "<red><b>▼ Fallende</b></red>", "<red><b>▼ Falling</b></red>");
        t("exchange.down.lore", "<mu>Viel verkauft – Vroni zahlt weniger</mu>", "<mu>Sold a lot – Vroni pays less</mu>");
        t("exchange.ores", "<au><b>◆ Erze & Edelsteine</b></au>", "<au><b>◆ Ores & gems</b></au>");
        t("exchange.ores.lore", "<mu>Kostbar – mit Abstand das Teuerste</mu>", "<mu>Precious – by far the priciest</mu>");
        t("exchange.calm", "<mu>Gerade ruhig</mu>", "<mu>Quiet right now</mu>");
        t("exchange.calm.lore", "<mu>Alle Kurse stehen beim Grundpreis</mu>", "<mu>All prices are at their base</mu>");
        t("exchange.cats", "<white><b>Alle Waren</b></white>", "<white><b>All goods</b></white>");
        t("exchange.cats.lore", "<mu>Nach Abteilung, mit Kurs und Verlauf</mu>", "<mu>By department, with price and history</mu>");
        t("exchange.search", "<white><b>Ware suchen</b></white>", "<white><b>Find goods</b></white>");
        t("exchange.search.ask", "Welche Ware? Tippe den Namen in den Chat <mu>(„abbrechen“ zum Abbrechen)</mu>.",
                "Which goods? Type the name in chat <mu>(“cancel” to cancel)</mu>.");
        t("exchange.search.none", "<red>Das hat Vroni nicht im Sortiment.</red> <mu>Unter Spielern: /ah suche \\<text></mu>",
                "<red>Vroni doesn't stock that.</red> <mu>Between players: /ah search \\<text></mu>");
        t("exchange.info", "<white><b>So funktioniert die Börse</b></white>", "<white><b>How the exchange works</b></white>");
        t("exchange.info.l1", "<mu>Jede Ware hat einen Kurs (100 % = Grundpreis).</mu>",
                "<mu>Every good has a rate (100 % = base price).</mu>");
        t("exchange.info.l2", "<mu>Vroni zahlt nie mehr als den Grundpreis</mu>",
                "<mu>Vroni never pays more than the base price</mu>");
        t("exchange.info.l3", "<mu>und verlangt nie weniger.</mu>", "<mu>and never charges less.</mu>");
        t("exchange.info.l4", "<mu>Mehr bekommst du unter Spielern: Auktionshaus, Aufträge.</mu>",
                "<mu>Players pay more: auction house, orders.</mu>");
        t("exchange.info.l5", "<mu>/wert zeigt den Wert, /verkaufen hand verkauft sofort.</mu>",
                "<mu>/wert shows the value, /verkaufen hand sells at once.</mu>");
        t("exchange.to-shop", "<em><b>Zum Händlerhaus</b></em>", "<em><b>To the merchant house</b></em>");
        t("exchange.to-ah", "<au><b>Auktionshaus</b></au>", "<au><b>Auction house</b></au>");
        t("exchange.to-orders", "<white><b>Aufträge</b></white>", "<white><b>Orders</b></white>");
        t("exchange.button", "<au><b>Börse</b></au>", "<au><b>Exchange</b></au>");
        t("exchange.button.lore", "<mu>Kurse, Steiger und Fallende, Verlauf</mu>", "<mu>Prices, risers and fallers, history</mu>");
        t("exchange.click", "<em>Klick: Verlauf und Handel</em>", "<em>Click: history and trade</em>");
    }

    private static void detail() {
        t("exchange.course.up", "<green>Kurs ▲ <pct> %</green> <mu>(Vroni verlangt mehr)</mu>",
                "<green>Rate ▲ <pct> %</green> <mu>(Vroni charges more)</mu>");
        t("exchange.course.down", "<red>Kurs ▼ <pct> %</red> <mu>(Vroni zahlt weniger)</mu>",
                "<red>Rate ▼ <pct> %</red> <mu>(Vroni pays less)</mu>");
        t("exchange.course.flat", "<mu>Kurs 100 % (Grundpreis)</mu>", "<mu>Rate 100 % (base price)</mu>");
        t("exchange.buy", "<white>Vroni verkauft:</white> <au><price></au>", "<white>Vroni sells:</white> <au><price></au>");
        t("exchange.sell", "<white>Vroni zahlt:</white> <em><price></em>", "<white>Vroni pays:</white> <em><price></em>");
        t("exchange.no-buy", "<mu>Vroni verkauft das nicht</mu>", "<mu>Vroni doesn't sell this</mu>");
        t("exchange.no-sell", "<mu>Vroni kauft das nicht an</mu>", "<mu>Vroni doesn't buy this</mu>");
        t("exchange.change", "<mu>24 Std.:</mu> <change>", "<mu>24 h:</mu> <change>");
        t("exchange.spark", "<mu>Verlauf 24 Std.:</mu> <bars>", "<mu>Last 24 h:</mu> <bars>");
        t("exchange.volume", "<mu>Heute bei Vroni: <bought> gekauft · <sold> verkauft</mu>",
                "<mu>Today at Vroni: <bought> bought · <sold> sold</mu>");
        t("exchange.market.median", "<white>Spieler-Marktpreis:</white> <au><price></au>",
                "<white>Player market price:</white> <au><price></au>");
        t("exchange.market.cheapest", "<white>Günstigstes Angebot:</white> <au><price></au> <mu>(<amount>× im Auktionshaus)</mu>",
                "<white>Cheapest offer:</white> <au><price></au> <mu>(<amount>× at the auction house)</mu>");
        t("exchange.market.order", "<white>Bester Kaufauftrag:</white> <em><price></em> <mu>(noch <amount> gesucht)</mu>",
                "<white>Best buy order:</white> <em><price></em> <mu>(<amount> still wanted)</mu>");
        t("exchange.market.none", "<mu>Unter Spielern noch nicht gehandelt</mu>", "<mu>Not traded between players yet</mu>");
        t("exchange.chart.title", "<item> <dark_gray>· Verlauf</dark_gray>", "<item> <dark_gray>· history</dark_gray>");
        t("exchange.chart.cell", "<white><when>:</white> <rate>", "<white><when>:</white> <rate>");
        t("exchange.chart.rate.up", "<green>Kurs <pct> %</green>", "<green>rate <pct> %</green>");
        t("exchange.chart.rate.down", "<red>Kurs <pct> %</red>", "<red>rate <pct> %</red>");
        t("exchange.chart.rate.flat", "<gray>Kurs 100 %</gray>", "<gray>rate 100 %</gray>");
        t("exchange.chart.now", "jetzt", "now");
        t("exchange.chart.ago", "vor <time>", "<time> ago");
        t("exchange.chart.week", "<white><b>7 Tage zeigen</b></white>", "<white><b>Show 7 days</b></white>");
        t("exchange.chart.day", "<white><b>24 Stunden zeigen</b></white>", "<white><b>Show 24 hours</b></white>");
        t("exchange.chart.showing.day", "<mu>Gerade: letzte 24 Stunden (alle 3 Std. ein Balken)</mu>",
                "<mu>Showing: last 24 hours (one bar every 3 h)</mu>");
        t("exchange.chart.showing.week", "<mu>Gerade: letzte 7 Tage (alle 21 Std. ein Balken)</mu>",
                "<mu>Showing: last 7 days (one bar every 21 h)</mu>");
        t("exchange.chart.button", "<au><b>Kursverlauf</b></au>", "<au><b>Price history</b></au>");
        t("exchange.chart.button.lore", "<mu>Klick: Verlauf an der Börse</mu>", "<mu>Click: history at the exchange</mu>");
        t("exchange.trade", "<em><b>Kaufen & verkaufen</b></em>", "<em><b>Buy & sell</b></em>");
        t("exchange.trade.lore", "<mu>bei Vroni im Händlerhaus</mu>", "<mu>with Vroni at the merchant house</mu>");
        t("exchange.ah.search", "<au><b>Im Auktionshaus suchen</b></au>", "<au><b>Search the auction house</b></au>");
        t("exchange.orders.search", "<white><b>Aufträge dazu</b></white>", "<white><b>Orders for this</b></white>");
        t("exchange.orders.search.lore", "<mu>Wer kauft das? Liefern bringt oft mehr</mu>",
                "<mu>Who buys this? Delivering often pays more</mu>");
    }

    private static void worth() {
        t("worth.none", "Nimm etwas in die Hand – dann sag ich dir, was es wert ist. <mu>(/wert alles: ganzes Inventar)</mu>",
                "Hold something in your hand and I'll tell you what it's worth. <mu>(/wert alles: whole inventory)</mu>");
        t("worth.head", "<au><b>Wert:</b></au> <white><amount>×</white> <item>", "<au><b>Worth:</b></au> <white><amount>×</white> <item>");
        t("worth.buy", " <mu>•</mu> Vroni verkauft: <au><price></au> <mu>pro Stück</mu><rate>",
                " <mu>•</mu> Vroni sells: <au><price></au> <mu>each</mu><rate>");
        t("worth.sell", " <mu>•</mu> Vroni zahlt: <em><price></em> <mu>pro Stück – für deine <amount>:</mu> <em><total></em><rate>",
                " <mu>•</mu> Vroni pays: <em><price></em> <mu>each – for your <amount>:</mu> <em><total></em><rate>");
        t("worth.rate.up", " <green>(▲ <pct> %)</green>", " <green>(▲ <pct> %)</green>");
        t("worth.rate.down", " <red>(▼ <pct> %)</red>", " <red>(▼ <pct> %)</red>");
        t("worth.no-sell", " <mu>• Vroni kauft das nicht an.</mu>", " <mu>• Vroni doesn't buy this.</mu>");
        t("worth.no-ware", " <mu>• Gibt es bei Vroni nicht.</mu>", " <mu>• Vroni doesn't trade this.</mu>");
        t("worth.special", " <mu>• Besonderer Gegenstand (Name, Verzauberung …) – nur unter Spielern.</mu>",
                " <mu>• Special item (name, enchantment …) – players only.</mu>");
        t("worth.spark", " <mu>• Kurs 24 Std.:</mu> <bars>", " <mu>• Rate 24 h:</mu> <bars>");
        t("worth.median", " <mu>•</mu> Spieler-Marktpreis: <au><price></au> <mu>(Mittel der letzten Verkäufe)</mu>",
                " <mu>•</mu> Player market price: <au><price></au> <mu>(median of recent sales)</mu>");
        t("worth.cheapest", " <mu>•</mu> Günstigstes Angebot im Auktionshaus: <au><price></au> <mu>pro Stück</mu>",
                " <mu>•</mu> Cheapest auction house offer: <au><price></au> <mu>each</mu>");
        t("worth.order", " <mu>•</mu> Bester Kaufauftrag: <em><price></em> <mu>pro Stück (noch <amount> gesucht)</mu>",
                " <mu>•</mu> Best buy order: <em><price></em> <mu>each (<amount> still wanted)</mu>");
        t("worth.no-market", " <mu>• Unter Spielern noch nicht gehandelt.</mu>", " <mu>• Not traded between players yet.</mu>");
        t("worth.actions",
                "<click:run_command:'/verkaufen hand'><hover:show_text:'Erst an Kaufaufträge, die mehr zahlen, dann an Vroni'><em>[Jetzt verkaufen]</em></hover></click> "
                        + "<click:run_command:'/boerse'><au>[Börse]</au></click>",
                "<click:run_command:'/verkaufen hand'><hover:show_text:'First to buy orders that pay more, then to Vroni'><em>[Sell now]</em></hover></click> "
                        + "<click:run_command:'/boerse'><au>[Exchange]</au></click>");
        t("worth.all", "Dein Inventar ist bei Vroni gerade <em><total></em> wert <mu>(<count> Sachen, <kinds> Sorten)</mu>. "
                        + "<click:run_command:'/verkaufen alles'><em>[Alles verkaufen]</em></click>",
                "Your inventory is worth <em><total></em> at Vroni right now <mu>(<count> items, <kinds> kinds)</mu>. "
                        + "<click:run_command:'/verkaufen alles'><em>[Sell all]</em></click>");
        t("worth.all.none", "Nichts in deinem Inventar kauft Vroni an.", "Vroni doesn't buy anything in your inventory.");
    }

    private static void quickSell() {
        t("quicksell.none", "Nimm erst etwas in die Hand. <mu>(/verkaufen alles: ganzes Inventar · /verkaufen: hineinlegen)</mu>",
                "Hold something first. <mu>(/verkaufen alles: whole inventory · /verkaufen: drop items in)</mu>");
        t("quicksell.orders", "An Kaufaufträge geliefert: <count>× <item> für <em>+<money></em> <mu>(Gebühr <fee>)</mu>",
                "Delivered to buy orders: <count>× <item> for <em>+<money></em> <mu>(fee <fee>)</mu>");
        t("quicksell.vroni", "An Vroni verkauft: <count>× <item> für <em>+<money></em>",
                "Sold to Vroni: <count>× <item> for <em>+<money></em>");
        t("quicksell.nobody", "Für <item> gibt es gerade keinen Käufer. <mu>Stell es ins Auktionshaus: /ah verkaufen \\<preis></mu>",
                "Nobody buys <item> right now. <mu>Put it in the auction house: /ah verkaufen \\<price></mu>");
        t("quicksell.balance", "<mu>Kontostand: <money></mu>", "<mu>Balance: <money></mu>");
    }

    private static void admin() {
        t("admin.help.boerse", " <au>boerse [zuruecksetzen [ware] | setzen \\<ware> \\<prozent>]</au> – Kurse der Börse",
                " <au>boerse [zuruecksetzen [ware] | setzen \\<ware> \\<percent>]</au> – exchange rates");
        t("admin.exchange", "Börse: <count> Waren mit Kurs ≠ 100 % <mu>(Grenzen <min>–<max> %, Hälfte nach <halve> Tagesmengen, Erholung <hours> Std.)</mu>",
                "Exchange: <count> goods with rate ≠ 100 % <mu>(limits <min>–<max> %, half after <halve> daily amounts, recovery <hours> h)</mu>");
        t("admin.exchange.off", "Börse ist aus (boerse.an: false) – Vroni handelt zum Grundpreis.",
                "Exchange is off (boerse.an: false) – Vroni trades at base prices.");
        t("admin.exchange.line", "<mu>•</mu> <item> <rate>", "<mu>•</mu> <item> <rate>");
        t("admin.exchange.reset", "Kurse zurückgesetzt: <count>", "Rates reset: <count>");
        t("admin.exchange.set", "Kurs von <item> steht jetzt bei <pct> %.", "Rate of <item> is now <pct> %.");
        t("admin.exchange.unknown", "<red>Diese Ware gibt es im Händlerhaus nicht.</red>", "<red>No such goods at the merchant house.</red>");
        t("admin.exchange.usage", "<red>So geht's:</red> /smpadmin boerse [zuruecksetzen [ware] | setzen \\<ware> \\<prozent>]",
                "<red>Usage:</red> /smpadmin boerse [zuruecksetzen [ware] | setzen \\<ware> \\<percent>]");
    }
}
