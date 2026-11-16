package de.moinmorni.smp.text;

/** Texte – Händlerhaus (Server-Shop, {@code /shop}). */
final class TextsShop {

    private TextsShop() {
    }

    private static void t(String key, String de, String en) {
        Texts.t(key, de, en);
    }

    static void register() {
        menus();
        ware();
        detail();
        messages();
        admin();
    }

    private static void menus() {
        t("merchant.title", "Händlerhaus", "Merchant house");
        t("merchant.title.category", "<cat> <dark_gray>(<page>/<pages>)</dark_gray>",
                "<cat> <dark_gray>(<page>/<pages>)</dark_gray>");
        t("merchant.title.search", "Suche: <query> <dark_gray>(<page>/<pages>)</dark_gray>",
                "Search: <query> <dark_gray>(<page>/<pages>)</dark_gray>");
        t("merchant.head", "<au><b>Händlerhaus der Marktstadt</b></au>", "<au><b>Market town merchant house</b></au>");
        t("merchant.head.l1", "<white>Kaufen zu festen Preisen,</white>", "<white>Buy at fixed prices,</white>");
        t("merchant.head.l2", "<white>verkaufen an die Händlerin.</white>", "<white>sell to the merchant.</white>");
        t("merchant.head.l3", "<mu><count> Waren in <cats> Abteilungen</mu>", "<mu><count> goods in <cats> departments</mu>");
        t("merchant.cat", "<au><b><cat></b></au>", "<au><b><cat></b></au>");
        t("merchant.cat.count", "<mu><count> Waren</mu>", "<mu><count> goods</mu>");
        t("merchant.cat.click", "<em>Klick: ansehen</em>", "<em>Click: browse</em>");
        t("merchant.search", "<white><b>Suchen</b></white>", "<white><b>Search</b></white>");
        t("merchant.search.lore", "<mu>Ware per Name finden (deutsch oder englisch)</mu>",
                "<mu>Find goods by name (German or English)</mu>");
        t("merchant.search.ask", "Wonach suchst du? Tippe es in den Chat <mu>(„abbrechen“ zum Abbrechen)</mu>.",
                "What are you looking for? Type it in chat <mu>(“cancel” to cancel)</mu>.");
        t("merchant.search.none", "<red>Dazu hat die Händlerin nichts.</red> <mu>Vielleicht im Auktionshaus: /ah suche \\<text></mu>",
                "<red>The merchant has nothing like that.</red> <mu>Maybe at the auction house: /ah search \\<text></mu>");
        t("merchant.balance", "<white>Dein Geld:</white> <au><b><money></b></au>", "<white>Your money:</white> <au><b><money></b></au>");
        t("merchant.balance.lore", "<mu>Geld verdienen: Sachen verkaufen, Aufträge beliefern, /belohnung</mu>",
                "<mu>Earn money: sell things, fill orders, /reward</mu>");
        t("merchant.sellin", "<gold><b>Ankauf: hineinlegen</b></gold>", "<gold><b>Buy-back: drop items in</b></gold>");
        t("merchant.sellin.lore", "<mu>Viele Sachen auf einmal hineinlegen und verkaufen</mu>",
                "<mu>Put many things in at once and sell them</mu>");
        t("merchant.sellall", "<gold><b>Inventar verkaufen</b></gold>", "<gold><b>Sell inventory</b></gold>");
        t("merchant.sellall.lore", "<mu>Alles, was die Händlerin ankauft – mit Übersicht vorher</mu>",
                "<mu>Everything the merchant buys – with a summary first</mu>");
        t("merchant.sellall.worth", "<white>Wert jetzt:</white> <em><money></em> <mu>(<count> Sachen)</mu>",
                "<white>Worth now:</white> <em><money></em> <mu>(<count> items)</mu>");
        t("merchant.market", "<white><b>Spieler-Markt</b></white>", "<white><b>Player market</b></white>");
        t("merchant.market.lore", "<mu>Im Auktionshaus bieten Spieler oft günstiger an</mu>",
                "<mu>At the auction house players often sell for less</mu>");
        t("merchant.info", "<white><b>So funktioniert's</b></white>", "<white><b>How it works</b></white>");
        t("merchant.info.l1", "<mu>Die Händlerin verkauft teuer und kauft billig –</mu>",
                "<mu>The merchant sells high and buys low –</mu>");
        t("merchant.info.l2", "<mu>unter Spielern handelt es sich besser.</mu>", "<mu>trading between players pays more.</mu>");
        t("merchant.info.l3", "<mu>Ankauf: pro Tag eine volle Menge, danach weniger.</mu>",
                "<mu>Buy-back: one full amount per day, then less.</mu>");
        t("merchant.info.l4", "<mu>Manche Waren gibt es nur zur Saison.</mu>", "<mu>Some goods are seasonal.</mu>");
        t("merchant.empty", "<white>Hier ist gerade nichts</white>", "<white>Nothing here right now</white>");
    }

    private static void ware() {
        t("merchant.ware.buy", "<white>Kaufen:</white> <au><price></au> <mu>pro Stück</mu>",
                "<white>Buy:</white> <au><price></au> <mu>each</mu>");
        t("merchant.ware.buy.stack", "<mu>  <amount> Stück: <price></mu>", "<mu>  <amount> pieces: <price></mu>");
        t("merchant.ware.no-buy", "<mu>Nicht zu kaufen</mu>", "<mu>Not for sale</mu>");
        t("merchant.ware.sell", "<white>Verkaufen:</white> <em><price></em> <mu>pro Stück</mu>",
                "<white>Sell:</white> <em><price></em> <mu>each</mu>");
        t("merchant.ware.sell.full", "<mu>  heute noch <full> zum vollen Preis</mu>", "<mu>  <full> more today at full price</mu>");
        t("merchant.ware.sell.less", "<mu>  Tagesmenge erreicht – jetzt weniger</mu>", "<mu>  daily amount reached – pays less now</mu>");
        t("merchant.ware.no-sell", "<mu>Kauft die Händlerin nicht an</mu>", "<mu>The merchant doesn't buy this</mu>");
        t("merchant.ware.limit", "<mu>Heute noch <left> zu haben</mu>", "<mu><left> left for you today</mu>");
        t("merchant.ware.sold-out", "<red>Für heute ausverkauft</red>", "<red>Sold out for today</red>");
        t("merchant.ware.season", "<light_purple>✿ Saison-Ware</light_purple>", "<light_purple>✿ Seasonal</light_purple>");
        t("merchant.ware.have", "<mu>Im Inventar: <count></mu>", "<mu>In your inventory: <count></mu>");
        t("merchant.ware.click", "<em>Klick: kaufen & verkaufen</em>", "<em>Click: buy & sell</em>");
        t("merchant.ware.shift", "<mu>Umschalt-Klick: <amount> sofort kaufen</mu>", "<mu>Shift-click: buy <amount> now</mu>");
    }

    private static void detail() {
        t("merchant.buy.header", "<em><b>Kaufen ▸</b></em>", "<em><b>Buy ▸</b></em>");
        t("merchant.buy.header.lore", "<mu><price> pro Stück</mu>", "<mu><price> each</mu>");
        t("merchant.buy.button", "<em><b>Kaufen: <amount>×</b></em>", "<em><b>Buy: <amount>×</b></em>");
        t("merchant.buy.stacks", "<em><b>Kaufen: <stacks> Stapel</b></em> <mu>(<amount>)</mu>",
                "<em><b>Buy: <stacks> stacks</b></em> <mu>(<amount>)</mu>");
        t("merchant.buy.fill", "<em><b>So viel, wie passt</b></em> <mu>(<amount>)</mu>",
                "<em><b>As much as fits</b></em> <mu>(<amount>)</mu>");
        t("merchant.buy.fill.none", "<mu>Kein Platz oder kein Geld</mu>", "<mu>No room or no money</mu>");
        t("merchant.buy.custom", "<em><b>Eigene Menge kaufen</b></em>", "<em><b>Buy a custom amount</b></em>");
        t("merchant.cost", "<white>Kosten:</white> <au><price></au>", "<white>Cost:</white> <au><price></au>");
        t("merchant.missing", "<red>Dir fehlen <missing></red>", "<red>You're short <missing></red>");
        t("merchant.room", "<red>Platz nur für <room></red>", "<red>Room for only <room></red>");
        t("merchant.sell.header", "<gold><b>Verkaufen ▸</b></gold>", "<gold><b>Sell ▸</b></gold>");
        t("merchant.sell.button", "<gold><b>Verkaufen: <amount>×</b></gold>", "<gold><b>Sell: <amount>×</b></gold>");
        t("merchant.sell.all", "<gold><b>Alle verkaufen</b></gold> <mu>(<amount>)</mu>",
                "<gold><b>Sell all</b></gold> <mu>(<amount>)</mu>");
        t("merchant.sell.custom", "<gold><b>Eigene Menge verkaufen</b></gold>", "<gold><b>Sell a custom amount</b></gold>");
        t("merchant.payout", "<white>Du bekommst:</white> <em><price></em>", "<white>You get:</white> <em><price></em>");
        t("merchant.have-only", "<red>Du hast nur <count></red>", "<red>You only have <count></red>");
        t("merchant.sell.no", "<mu>Kauft die Händlerin nicht an</mu>", "<mu>The merchant doesn't buy this</mu>");
        t("merchant.sell.no.lore", "<mu>Biete es im Auktionshaus an: /ah verkaufen \\<preis></mu>",
                "<mu>Sell it at the auction house: /ah sell \\<price></mu>");
        t("merchant.ah", "<white><b>Im Auktionshaus suchen</b></white>", "<white><b>Search the auction house</b></white>");
        t("merchant.ah.price", "<mu>Marktpreis unter Spielern: <price> pro Stück</mu>",
                "<mu>Market price between players: <price> each</mu>");
        t("merchant.ah.no-price", "<mu>Noch kein Marktpreis unter Spielern</mu>", "<mu>No market price between players yet</mu>");
    }

    private static void messages() {
        t("merchant.bought", "<em>✔</em> Gekauft: <white><amount>× <item></white> für <au><price></au>. Noch <au><balance></au>.",
                "<em>✔</em> Bought <white><amount>× <item></white> for <au><price></au>. <au><balance></au> left.");
        t("merchant.sold", "<em>✔</em> Verkauft: <white><amount>× <item></white> für <em>+<price></em>.",
                "<em>✔</em> Sold <white><amount>× <item></white> for <em>+<price></em>.");
        t("merchant.sold-all", "<em>✔</em> <count> Sachen (<kinds> Sorten) verkauft: <em>+<price></em>",
                "<em>✔</em> Sold <count> items (<kinds> kinds): <em>+<price></em>");
        t("merchant.too-poor", "<red>Dafür reicht dein Geld nicht – dir fehlen <missing>.</red>",
                "<red>You can't afford that – you're short <missing>.</red>");
        t("merchant.no-room", "<red>So viel passt nicht in dein Inventar (Platz für <room>).</red>",
                "<red>That doesn't fit in your inventory (room for <room>).</red>");
        t("merchant.limit", "<red>Davon gibt's heute nur noch <left> für dich – morgen wieder mehr.</red>",
                "<red>Only <left> more of that for you today – more tomorrow.</red>");
        t("merchant.not-enough", "<red>Du hast nur <count> davon.</red>", "<red>You only have <count> of those.</red>");
        t("merchant.nothing-to-sell", "<red>Du hast nichts dabei, was die Händlerin ankauft.</red>",
                "<red>You have nothing the merchant buys.</red>");
        t("merchant.no-buy", "<red>Das verkauft die Händlerin nicht.</red>", "<red>The merchant doesn't sell that.</red>");
        t("merchant.no-sell", "<red>Das kauft die Händlerin nicht an.</red> <mu>Versuch's im Auktionshaus: /ah</mu>",
                "<red>The merchant doesn't buy that.</red> <mu>Try the auction house: /ah</mu>");
        t("merchant.off", "<red>Das Händlerhaus hat gerade geschlossen.</red>", "<red>The merchant house is closed right now.</red>");
        t("merchant.off-season", "<red>Das gibt es gerade nicht – Saison-Ware.</red>", "<red>Not available right now – seasonal.</red>");
        t("merchant.amount.ask", "Wie viele? Tippe eine Zahl in den Chat <mu>(1–<max>, „abbrechen“ zum Abbrechen)</mu>.",
                "How many? Type a number in chat <mu>(1–<max>, “cancel” to cancel)</mu>.");
        t("merchant.amount.bad", "<red>Das ist keine gültige Menge (1–<max>).</red>", "<red>That's not a valid amount (1–<max>).</red>");
        t("merchant.confirm.buy", "Wirklich kaufen?", "Really buy?");
        t("merchant.confirm.buy.yes", "<em><b>Ja, für <price> kaufen</b></em>", "<em><b>Yes, buy for <price></b></em>");
        t("merchant.confirm.sell", "Wirklich verkaufen?", "Really sell?");
        t("merchant.confirm.sell.yes", "<em><b>Ja, für <price> verkaufen</b></em>", "<em><b>Yes, sell for <price></b></em>");
        t("merchant.confirm.line", "<white><amount>× <item></white> <em><price></em>", "<white><amount>× <item></white> <em><price></em>");
        t("merchant.confirm.more", "<mu>… und <count> weitere Sorten</mu>", "<mu>… and <count> more kinds</mu>");
        t("merchant.confirm.total", "<white>Zusammen:</white> <em><price></em>", "<white>Total:</white> <em><price></em>");
        t("merchant.unknown-category", "<red>Diese Abteilung gibt es nicht.</red> <mu>Abteilungen: <list></mu>",
                "<red>There's no such department.</red> <mu>Departments: <list></mu>");
        t("merchant.npc.hint", "<au><b>Händlerin Vroni</b></au>\n<white>Rechtsklick: Kaufen & Verkaufen</white>\n"
                + "<mu>Right-click: buy & sell · /shop</mu>", "<au><b>Vroni the merchant</b></au>\n"
                + "<white>Right-click: buy & sell</white>\n<mu>Rechtsklick: Kaufen & Verkaufen · /shop</mu>");
        t("mail.reason.haendler", "<mu>gekauft im Händlerhaus</mu>", "<mu>bought at the merchant house</mu>");
        t("help.haendler", " <au>/shop</au> Händlerhaus: kaufen zu festen Preisen, verkaufen an die Händlerin",
                " <au>/shop</au> merchant house: buy at fixed prices, sell to the merchant");
    }

    private static void admin() {
        t("admin.help.shop", " <au>shop</au> – Umsatz des Händlerhauses heute", " <au>shop</au> – merchant house turnover today");
        t("admin.shop", "<au>Händlerhaus heute:</au> Spieler kauften für <au><income></au> (<buys> Käufe), "
                + "die Händlerin zahlte <em><payout></em> (<sells> Verkäufe). <mu><wares> Waren im Sortiment.</mu>",
                "<au>Merchant house today:</au> players bought for <au><income></au> (<buys> purchases), "
                + "the merchant paid <em><payout></em> (<sells> sales). <mu><wares> goods in stock.</mu>");
        t("admin.shop.top", " <white><item></white> <mu>gekauft <bought> · verkauft <sold></mu>",
                " <white><item></white> <mu>bought <bought> · sold <sold></mu>");
    }
}
