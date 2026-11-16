package de.moinmorni.smp.text;

/** Texte – Markt: Auktionshaus, Aufträge, Ankauf, Läden, Marktstände, Abholfach und gespeicherte Nachrichten. */
final class TextsMarket {

    private TextsMarket() {
    }

    private static void t(String key, String de, String en) {
        Texts.t(key, de, en);
    }

    static void register() {
        auction();
        orders();
        buyback();
        shops();
        stalls();
        mailbox();
    }

    private static void auction() {
        t("ah.title", "Auktionshaus <dark_gray>(<page>/<pages>)</dark_gray>", "Auction house <dark_gray>(<page>/<pages>)</dark_gray>");
        t("ah.button", "<au><b>Auktionshaus</b></au>", "<au><b>Auction house</b></au>");
        t("ah.button.lore", "<mu>Kaufen und verkaufen, was Spieler anbieten</mu>", "<mu>Buy and sell what players offer</mu>");
        t("ah.empty", "<white>Hier ist gerade nichts</white>", "<white>Nothing here right now</white>");
        t("ah.empty.lore", "<mu>Sei die erste Person, die etwas anbietet!</mu>", "<mu>Be the first to offer something!</mu>");
        t("ah.sort.cheapest", "<white>Sortiert: billigste zuerst</white>", "<white>Sorted: cheapest first</white>");
        t("ah.sort.newest", "<white>Sortiert: neueste zuerst</white>", "<white>Sorted: newest first</white>");
        t("ah.sort.per_piece", "<white>Sortiert: Preis pro Stück</white>", "<white>Sorted: price per piece</white>");
        t("ah.sort.lore", "<mu>Klick: anders sortieren</mu>", "<mu>Click: change sorting</mu>");
        t("ah.mine", "<white>Meine Angebote</white>", "<white>My listings</white>");
        t("ah.mine.lore", "<mu><count> von <max> Plätzen belegt</mu>", "<mu><count> of <max> slots used</mu>");
        t("ah.mine.title", "Meine Angebote (<count>/<max>)", "My listings (<count>/<max>)");
        t("ah.sell", "<em><b>Verkaufen</b></em>", "<em><b>Sell</b></em>");
        t("ah.sell.lore", "<mu>Nimm den Gegenstand in die Hand und klick hier</mu>",
                "<mu>Hold the item in your hand and click here</mu>");
        t("ah.info", "<white>So funktioniert's</white>", "<white>How it works</white>");
        t("ah.info.lore", "<mu>Gebühr <fee> vom Erlös · Angebote stehen <hours> Std. · Dein Geld: <au><balance></au></mu>",
                "<mu>Fee <fee> of the price · listings last <hours> h · Your money: <au><balance></au></mu>");
        t("ah.line.price", "<white>Preis:</white> <au><price></au>", "<white>Price:</white> <au><price></au>");
        t("ah.line.unit", "<mu>pro Stück: <unit></mu>", "<mu>per piece: <unit></mu>");
        t("ah.line.market", "<mu>Marktpreis: <unit> pro Stück</mu>", "<mu>Market price: <unit> per piece</mu>");
        t("ah.line.seller", "<mu>von <white><name></white></mu>", "<mu>by <white><name></white></mu>");
        t("ah.line.left", "<mu>noch <time></mu>", "<mu><time> left</mu>");
        t("ah.line.buy", "<em>Klick: kaufen</em>", "<em>Click: buy</em>");
        t("ah.line.own", "<au>Dein Angebot – Klick: zurücknehmen</au>", "<au>Your listing – click: take back</au>");
        t("ah.confirm.title", "Wirklich kaufen?", "Really buy?");
        t("ah.confirm.yes", "<em><b>Ja, für <price> kaufen</b></em>", "<em><b>Yes, buy for <price></b></em>");
        t("ah.bought", "<em>✔</em> Gekauft: <white><amount>× <item></white> für <au><price></au> von <white><seller></white>.",
                "<em>✔</em> Bought <white><amount>× <item></white> for <au><price></au> from <white><seller></white>.");
        t("ah.gone", "<red>Schade – das Angebot gibt es nicht mehr.</red>", "<red>Too bad – that listing is gone.</red>");
        t("ah.own", "<red>Das ist dein eigenes Angebot.</red>", "<red>That's your own listing.</red>");
        t("ah.sell.title", "Preis wählen", "Choose a price");
        t("ah.sell.empty-hand", "<red>Nimm zuerst den Gegenstand in die Hand, den du verkaufen willst.</red>",
                "<red>First hold the item you want to sell.</red>");
        t("ah.sell.market-total", "<mu>Marktpreis für alles: <price></mu>", "<mu>Market price for all: <price></mu>");
        t("ah.sell.no-market", "<mu>Noch kein Marktpreis – du bestimmst!</mu>", "<mu>No market price yet – you decide!</mu>");
        t("ah.sell.you-get", "<white>Du bekommst:</white> <au><net></au> <mu>(Gebühr <fee>)</mu>",
                "<white>You get:</white> <au><net></au> <mu>(fee <fee>)</mu>");
        t("ah.sell.changed", "<red>Der Gegenstand in deiner Hand hat sich geändert – nochmal bitte.</red>",
                "<red>The item in your hand changed – please try again.</red>");
        t("ah.too-many", "<red>Du hast schon <max> Angebote. Warte, bis eins verkauft ist, oder nimm eins zurück.</red>",
                "<red>You already have <max> listings. Wait for a sale or take one back.</red>");
        t("ah.listed", "<em>✔</em> Angeboten: <white><amount>× <item></white> für <au><price></au> "
                + "<mu>(Gebühr beim Verkauf: <fee>, steht <hours> Std.)</mu>",
                "<em>✔</em> Listed <white><amount>× <item></white> for <au><price></au> "
                        + "<mu>(fee on sale: <fee>, listed for <hours> h)</mu>");
        t("ah.cancel.title", "Angebot zurücknehmen?", "Take listing back?");
        t("ah.cancel.lore", "<mu>Der Gegenstand kommt zurück zu dir</mu>", "<mu>The item comes back to you</mu>");
        t("ah.cancel.yes", "<em><b>Ja, zurücknehmen</b></em>", "<em><b>Yes, take it back</b></em>");
        t("ah.cancelled", "<em>✔</em> Angebot zurückgenommen.", "<em>✔</em> Listing taken back.");
        t("notice.ah.sold", "<em>✔ Verkauft!</em> <white><amount>× <item></white> an <white><buyer></white> für "
                + "<au><price></au> <mu>(Gebühr <fee>)</mu>.",
                "<em>✔ Sold!</em> <white><amount>× <item></white> to <white><buyer></white> for <au><price></au> "
                        + "<mu>(fee <fee>)</mu>.");
        t("notice.ah.removed", "Das Team hat dein Angebot <white><amount>× <item></white> entfernt – es liegt im "
                + "Abholfach (<au>/abholen</au>).",
                "The team removed your listing <white><amount>× <item></white> – it's in your mailbox "
                        + "(<au>/collect</au>).");
        t("notice.ah.expired", "Dein Angebot <white><amount>× <item></white> ist abgelaufen und liegt im Abholfach "
                + "(<au>/abholen</au>).",
                "Your listing <white><amount>× <item></white> expired and is in your mailbox (<au>/collect</au>).");
    }

    private static void orders() {
        t("orders.title", "Auftragsbrett <dark_gray>(<page>/<pages>)</dark_gray>", "Order board <dark_gray>(<page>/<pages>)</dark_gray>");
        t("orders.button", "<bl><b>Auftragsbrett</b></bl>", "<bl><b>Order board</b></bl>");
        t("orders.button.lore", "<mu>Spieler suchen Ware – liefern und Geld bekommen</mu>",
                "<mu>Players want goods – deliver and get paid</mu>");
        t("orders.empty", "<white>Gerade sucht niemand etwas</white>", "<white>Nobody is looking for anything</white>");
        t("orders.empty.lore", "<mu>Gib selbst einen Auftrag auf!</mu>", "<mu>Place an order yourself!</mu>");
        t("orders.sort.newest", "<white>Sortiert: neueste zuerst</white>", "<white>Sorted: newest first</white>");
        t("orders.sort.best", "<white>Sortiert: beste Bezahlung</white>", "<white>Sorted: best pay</white>");
        t("orders.mine", "<white>Meine Aufträge</white>", "<white>My orders</white>");
        t("orders.mine.lore", "<mu><count> von <max></mu>", "<mu><count> of <max></mu>");
        t("orders.mine.title", "Meine Aufträge (<count>/<max>)", "My orders (<count>/<max>)");
        t("orders.new", "<em><b>Auftrag aufgeben</b></em>", "<em><b>Place an order</b></em>");
        t("orders.new.lore", "<mu>Du zahlst vorab in die Treuhand, Lieferanten bekommen es Stück für Stück</mu>",
                "<mu>You pay into escrow up front, suppliers get paid piece by piece</mu>");
        t("orders.info", "<white>So funktioniert's</white>", "<white>How it works</white>");
        t("orders.info.lore", "<mu>Lieferanten zahlen <fee> Gebühr · Dein Geld: <au><balance></au></mu>",
                "<mu>Suppliers pay a <fee> fee · Your money: <au><balance></au></mu>");
        t("orders.line.wanted", "<white>Gesucht:</white> <au><left></au> <mu>von <wanted></mu>",
                "<white>Wanted:</white> <au><left></au> <mu>of <wanted></mu>");
        t("orders.line.pays", "<white>Zahlt:</white> <au><unit></au> <mu>pro Stück</mu>",
                "<white>Pays:</white> <au><unit></au> <mu>per piece</mu>");
        t("orders.line.total", "<mu>Noch in der Treuhand: <total></mu>", "<mu>Still in escrow: <total></mu>");
        t("orders.line.from", "<mu>von <white><name></white></mu>", "<mu>by <white><name></white></mu>");
        t("orders.line.have", "<mu>Du hast davon: <count></mu>", "<mu>You have: <count></mu>");
        t("orders.line.deliver", "<em>Klick: liefern</em>", "<em>Click: deliver</em>");
        t("orders.line.own", "<au>Dein Auftrag – Klick: abbrechen</au>", "<au>Your order – click: cancel</au>");
        t("orders.deliver.title", "Liefern", "Deliver");
        t("orders.deliver.none", "<red>Davon hast du nichts dabei</red>", "<red>You don't have any of that</red>");
        t("orders.deliver.none.lore", "<mu>Genau dieser Gegenstand muss im Inventar sein</mu>",
                "<mu>This exact item must be in your inventory</mu>");
        t("orders.deliver.all", "<em><b>Alles liefern (<count>)</b></em>", "<em><b>Deliver all (<count>)</b></em>");
        t("orders.deliver.stack", "<white>Einen Stapel liefern (<count>)</white>", "<white>Deliver one stack (<count>)</white>");
        t("orders.deliver.choose", "<au>Menge wählen</au>", "<au>Choose amount</au>");
        t("orders.deliver.gets", "<mu>Du bekommst</mu> <au><money></au>", "<mu>You get</mu> <au><money></au>");
        t("orders.delivered", "<em>✔</em> <white><count>× <item></white> an <white><owner></white> geliefert: "
                + "<au>+<money></au> <mu>(Gebühr <fee>)</mu>",
                "<em>✔</em> Delivered <white><count>× <item></white> to <white><owner></white>: <au>+<money></au> "
                        + "<mu>(fee <fee>)</mu>");
        t("orders.gone", "<red>Schade – der Auftrag ist schon erledigt oder abgelaufen.</red>",
                "<red>Too bad – that order is already done or expired.</red>");
        t("orders.own", "<red>Das ist dein eigener Auftrag.</red>", "<red>That's your own order.</red>");
        t("orders.cancel.title", "Auftrag abbrechen?", "Cancel order?");
        t("orders.cancel.lore", "<mu><money> kommen zurück auf dein Konto</mu>", "<mu><money> go back to your account</mu>");
        t("orders.cancel.yes", "<em><b>Ja, abbrechen</b></em>", "<em><b>Yes, cancel</b></em>");
        t("orders.cancelled", "<em>✔</em> Auftrag abgebrochen, <au><money></au> sind zurück.",
                "<em>✔</em> Order cancelled, <au><money></au> returned.");
        t("orders.new.ask-name", "Was suchst du? Schreib den Namen in den Chat (z. B. „eisenbarren“) – oder halte den "
                + "Gegenstand in der Hand und tippe <au>/auftraege neu \\<menge> \\<preis></au>.",
                "What do you want? Write the name in chat (e.g. “iron ingot”) – or hold the item and type "
                        + "<au>/orders new \\<amount> \\<price></au>.");
        t("orders.new.not-found", "<red>Zu „<text>“ finde ich nichts – versuch es anders.</red>",
                "<red>Nothing found for “<text>” – try something else.</red>");
        t("orders.new.pick", "Welcher Gegenstand?", "Which item?");
        t("orders.new.amount", "Wie viele?", "How many?");
        t("orders.new.price", "Preis pro Stück", "Price per piece");
        t("orders.new.summary", "<white><amount> Stück, zusammen</white> <au><total></au>",
                "<white><amount> pieces, total</white> <au><total></au>");
        t("orders.new.confirm", "Auftrag aufgeben?", "Place order?");
        t("orders.new.escrow", "<mu>Das Geld liegt bis zur Lieferung sicher in der Treuhand</mu>",
                "<mu>The money stays safely in escrow until delivery</mu>");
        t("orders.new.yes", "<em><b>Ja, <total> zurücklegen</b></em>", "<em><b>Yes, reserve <total></b></em>");
        t("orders.new.empty-hand", "<red>Halte den gesuchten Gegenstand in der Hand (als Vorlage).</red>",
                "<red>Hold the item you want in your hand (as a template).</red>");
        t("orders.new.invalid", "<red>Menge 1–<max> und ein Preis über 0, bitte.</red>",
                "<red>Amount 1–<max> and a price above 0, please.</red>");
        t("orders.created", "<em>✔</em> Auftrag steht: <white><amount>× <item></white> zu <au><unit></au> pro Stück "
                + "<mu>(<total> in der Treuhand)</mu>.",
                "<em>✔</em> Order placed: <white><amount>× <item></white> at <au><unit></au> each "
                        + "<mu>(<total> in escrow)</mu>.");
        t("orders.too-many", "<red>Du hast schon <max> Aufträge offen.</red>", "<red>You already have <max> open orders.</red>");
        t("notice.order.delivered", "<white><from></white> hat <white><amount>× <item></white> für deinen Auftrag "
                + "geliefert (<delivered>/<total>) – abholen mit <au>/abholen</au>.",
                "<white><from></white> delivered <white><amount>× <item></white> for your order (<delivered>/<total>) "
                        + "– collect with <au>/collect</au>.");
        t("notice.order.filled", "<em>✔ Auftrag erfüllt!</em> <white><from></white> hat die letzten "
                + "<white><amount>× <item></white> geliefert (<delivered>/<total>) – abholen mit <au>/abholen</au>.",
                "<em>✔ Order filled!</em> <white><from></white> delivered the last <white><amount>× <item></white> "
                        + "(<delivered>/<total>) – collect with <au>/collect</au>.");
        t("notice.order.expired", "Dein Auftrag für <white><item></white> ist abgelaufen – <au><refund></au> sind "
                + "zurück auf deinem Konto.",
                "Your order for <white><item></white> expired – <au><refund></au> went back to your account.");
    }

    private static void buyback() {
        t("buyback.title", "Ankauf – Händlerhaus",
                "Buy-back – merchant house");
        t("buyback.prices", "<white>Preise im Händlerhaus</white>",
                "<white>Prices at the merchant house</white>");
        t("buyback.prices.lore", "<mu>Klick: alle Preise und Tagesmengen ansehen</mu>",
                "<mu>Click: see all prices and daily amounts</mu>");
        t("buyback.put-in", "<mu>Lege oben hinein, was die Händlerin ankauft</mu>",
                "<mu>Put in above what the merchant buys</mu>");
        t("buyback.line", "<white><amount>× <item></white> <au><money></au>", "<white><amount>× <item></white> <au><money></au>");
        t("buyback.click", "<em>Klick: verkaufen</em>", "<em>Click: sell</em>");
        t("buyback.total", "<em><b>Verkaufen für <money></b></em>", "<em><b>Sell for <money></b></em>");
        t("buyback.nothing", "<red>Davon kauft die Händlerin nichts.</red> <mu>Was sie nimmt, steht im Händlerhaus: /shop</mu>",
                "<red>The merchant doesn't buy any of that.</red> <mu>See what she takes: /shop</mu>");
        t("buyback.sold", "<em>✔</em> <count> Sachen an die Händlerin verkauft: <au>+<money></au>",
                "<em>✔</em> Sold <count> items to the merchant: <au>+<money></au>");
        t("buyback.returned", "Was die Händlerin nicht kauft, hast du zurück.",
                "You got back what the merchant doesn't buy.");
    }

    private static void shops() {
        t("shop.no-container", "<red>Das Schild muss an oder auf einer Kiste oder einem Fass sein.</red>",
                "<red>The sign must be on or above a chest or barrel.</red>");
        t("shop.not-here", "<red>Einen Laden kannst du nur auf deinem Grundstück oder an deinem Marktstand eröffnen.</red>",
                "<red>You can only open a shop on your own land or at your market stall.</red>");
        t("shop.exists", "<red>An dieser Kiste ist schon ein Laden.</red>", "<red>This chest already has a shop.</red>");
        t("shop.sign.setup", "einrichten…", "set up…");
        t("shop.hold-item", "Halte die Ware in der Hand und klick das Schild an, um den Laden einzurichten.",
                "Hold the goods and click the sign to set up the shop.");
        t("shop.setup.title", "Laden einrichten", "Set up shop");
        t("shop.setup.item", "<mu>Das ist die Ware</mu>", "<mu>These are the goods</mu>");
        t("shop.mode.sell", "<em><b>Verkaufen</b></em>", "<em><b>Selling</b></em>");
        t("shop.mode.buy", "<bl><b>Ankaufen</b></bl>", "<bl><b>Buying</b></bl>");
        t("shop.mode.sell.lore", "<mu>Kunden kaufen aus deiner Kiste</mu>", "<mu>Customers buy from your chest</mu>");
        t("shop.mode.buy.lore", "<mu>Kunden verkaufen dir in die Kiste (du zahlst)</mu>",
                "<mu>Customers sell into your chest (you pay)</mu>");
        t("shop.mode.toggle", "<au>Klick: umschalten</au>", "<au>Click: switch</au>");
        t("shop.setup.amount", "<white>Menge pro Kauf: <au><amount></au></white>", "<white>Amount per trade: <au><amount></au></white>");
        t("shop.setup.price", "<white>Preis: <au><price></au></white>", "<white>Price: <au><price></au></white>");
        t("shop.setup.open", "<em><b>✔ Laden eröffnen</b></em>", "<em><b>✔ Open shop</b></em>");
        t("shop.sign.sells", "<white>Verkauft <amount> Stück für <au><price></au></white>",
                "<white>Sells <amount> for <au><price></au></white>");
        t("shop.sign.buys", "<white>Kauft <amount> Stück für <au><price></au></white>",
                "<white>Buys <amount> for <au><price></au></white>");
        t("shop.remove", "<red>Laden schließen</red>", "<red>Close shop</red>");
        t("shop.removed", "Laden geschlossen.", "Shop closed.");
        t("shop.gone", "<red>Diesen Laden gibt es nicht mehr.</red>", "<red>This shop no longer exists.</red>");
        t("shop.opened", "<em>✔</em> Dein Laden für <white><item></white> ist offen!",
                "<em>✔</em> Your shop for <white><item></white> is open!");
        t("shop.buy.title", "Laden von <owner>", "<owner>'s shop");
        t("shop.sell.title", "<owner> kauft an", "<owner> is buying");
        t("shop.stock", "<mu>Vorrat: <count>× kaufbar</mu>", "<mu>Stock: <count> purchases</mu>");
        t("shop.space", "<mu>Platz: <count>× verkaufbar</mu>", "<mu>Room: <count> sales</mu>");
        t("shop.buy.button", "<em><b>Kaufen: <amount> für <price></b></em>", "<em><b>Buy: <amount> for <price></b></em>");
        t("shop.sell.button", "<bl><b>Verkaufen: <amount> für <price></b></bl>", "<bl><b>Sell: <amount> for <price></b></bl>");
        t("shop.sold-out", "<red>Ausverkauft.</red>", "<red>Sold out.</red>");
        t("shop.sell.missing", "<red>Du brauchst <amount>× <item> im Inventar.</red>",
                "<red>You need <amount>× <item> in your inventory.</red>");
        t("shop.full", "<red>Die Kiste des Ladens ist voll.</red>", "<red>The shop's chest is full.</red>");
        t("shop.owner-broke", "<red>Der Laden kann gerade nicht bezahlen.</red>", "<red>The shop can't pay right now.</red>");
        t("shop.protected", "<red>Das ist ein Laden – nur der Besitzer kann ihn abbauen.</red>",
                "<red>That's a shop – only its owner can remove it.</red>");
        t("shop.bought", "<em>✔</em> Gekauft: <white><amount>× <item></white> für <au><price></au> bei <white><owner></white>.",
                "<em>✔</em> Bought <white><amount>× <item></white> for <au><price></au> from <white><owner></white>.");
        t("shop.sold", "<em>✔</em> Verkauft: <white><amount>× <item></white> für <au><price></au> an <white><owner></white>.",
                "<em>✔</em> Sold <white><amount>× <item></white> for <au><price></au> to <white><owner></white>.");
        t("notice.shop.sold", "<em>✔</em> Dein Laden hat <white><amount>× <item></white> an <white><buyer></white> "
                + "verkauft: <au>+<price></au>",
                "<em>✔</em> Your shop sold <white><amount>× <item></white> to <white><buyer></white>: <au>+<price></au>");
        t("notice.shop.bought", "<em>✔</em> Dein Laden hat <white><amount>× <item></white> von <white><seller></white> "
                + "angekauft: <au>−<price></au>",
                "<em>✔</em> Your shop bought <white><amount>× <item></white> from <white><seller></white>: "
                        + "<au>−<price></au>");
    }

    private static void stalls() {
        t("stall.title", "Marktstand <id>", "Market stall <id>");
        t("stall.free", "<em>frei</em> <mu>– <rent> pro Woche</mu>", "<em>free</em> <mu>– <rent> per week</mu>");
        t("stall.tenant", "<white>Mieter: <name></white>", "<white>Tenant: <name></white>");
        t("stall.until", "<mu>bezahlt für noch <until></mu>", "<mu>paid for <until> more</mu>");
        t("stall.explain", "<mu>Mieter dürfen hier Kisten, Fässer und Schilder aufstellen und Läden eröffnen.</mu>",
                "<mu>Tenants may place chests, barrels and signs here and open shops.</mu>");
        t("stall.rent.button", "<em><b>Mieten für <rent> / Woche</b></em>", "<em><b>Rent for <rent> / week</b></em>");
        t("stall.extend.button", "<em><b>Eine Woche verlängern (<rent>)</b></em>", "<em><b>Extend one week (<rent>)</b></em>");
        t("stall.release.button", "<red>Kündigen</red>", "<red>Give up</red>");
        t("stall.release.title", "Stand kündigen?", "Give up stall?");
        t("stall.release.lore", "<mu>Kisten, Inhalt und Schilder kommen ins Abholfach. Kein Geld zurück.</mu>",
                "<mu>Chests, contents and signs go to your mailbox. No refund.</mu>");
        t("stall.release.yes", "<red><b>Ja, kündigen</b></red>", "<red><b>Yes, give it up</b></red>");
        t("stall.released", "Stand gekündigt. <count> Einträge liegen im Abholfach.",
                "Stall given up. <count> entries are in your mailbox.");
        t("stall.rented", "<em>✔</em> Stand <id> gehört dir für <until> (<rent>). Stell Kisten mit "
                + "<au>[Laden]</au>-Schild auf!",
                "<em>✔</em> Stall <id> is yours for <until> (<rent>). Put up chests with a <au>[Shop]</au> sign!");
        t("stall.extended", "<em>✔</em> Stand <id> verlängert – bezahlt für noch <until>.",
                "<em>✔</em> Stall <id> extended – paid for <until> more.");
        t("stall.taken", "<red>Stand <id> hat schon <name> gemietet.</red>", "<red>Stall <id> is already rented by <name>.</red>");
        t("stall.has-other", "<red>Du hast schon Stand <id>. Einer pro Person.</red>",
                "<red>You already rent stall <id>. One per person.</red>");
        t("stall.too-far", "<red>So weit im Voraus geht nicht.</red>", "<red>You can't pay that far ahead.</red>");
        t("stall.none", "<red>Du hast keinen Marktstand.</red>", "<red>You don't rent a market stall.</red>");
        t("stall.stand-there", "<red>Stell dich vor einen Marktstand (am Marktplatz).</red>",
                "<red>Stand in front of a market stall (on the market square).</red>");
        t("stall.help", "Marktstände am Marktplatz kosten <au><rent></au> pro Woche. Stell dich davor und tippe "
                + "<au>/stand mieten</au> – oder klick auf das Schild am Stand.",
                "Market stalls cost <au><rent></au> per week. Stand in front of one and type <au>/stall rent</au> – "
                        + "or click the stall's sign.");
        t("notice.stall.warn", "Die Miete für Stand <id> läuft bald ab – am Stand <au>/stand verlaengern</au>.",
                "The rent for stall <id> runs out soon – at the stall: <au>/stall extend</au>.");
        t("notice.stall.expired", "Die Miete für Stand <id> ist abgelaufen. Deine Sachen (<count> Einträge) liegen im "
                + "Abholfach.", "The rent for stall <id> ran out. Your things (<count> entries) are in your mailbox.");
    }

    private static void mailbox() {
        t("mail.title", "Abholfach (<count>)", "Mailbox (<count>)");
        t("mail.button", "<au>Abholfach</au>", "<au>Mailbox</au>");
        t("mail.button.lore", "<mu><count> Einträge warten</mu>", "<mu><count> entries waiting</mu>");
        t("mail.line.amount", "<white><amount> Stück</white>", "<white><amount> pieces</white>");
        t("mail.line.take", "<em>Klick: ins Inventar</em>", "<em>Click: take</em>");
        t("mail.empty", "<white>Dein Abholfach ist leer</white>", "<white>Your mailbox is empty</white>");
        t("mail.full", "<red>Dein Inventar ist voll – der Rest bleibt im Abholfach.</red>",
                "<red>Your inventory is full – the rest stays in the mailbox.</red>");
        t("mail.take-all", "<em><b>Alles abholen</b></em>", "<em><b>Take everything</b></em>");
        t("mail.take-all.lore", "<mu>So viel ins Inventar passt</mu>", "<mu>As much as fits</mu>");
        t("mail.taken-all", "<em>✔</em> <count> Einträge abgeholt.", "<em>✔</em> Took <count> entries.");
        t("mail.taken-some", "<count> Einträge abgeholt – <left> passen nicht mehr ins Inventar.",
                "Took <count> entries – <left> don't fit into your inventory.");
        t("mail.waiting", "Nicht alles passte ins Inventar – der Rest liegt im Abholfach (<au>/abholen</au>).",
                "Not everything fit – the rest is in your mailbox (<au>/collect</au>).");
        t("mail.overflow", "Inventar voll – der Rest liegt im Abholfach (<au>/abholen</au>).",
                "Inventory full – the rest is in your mailbox (<au>/collect</au>).");
        t("mail.reminder", "In deinem Abholfach liegen <au><count></au> Einträge – <au>/abholen</au>.",
                "There are <au><count></au> entries in your mailbox – <au>/collect</au>.");
        t("mail.reason.ah_kauf", "<mu>gekauft im Auktionshaus</mu>", "<mu>bought at the auction house</mu>");
        t("mail.reason.ah_abgelaufen", "<mu>Angebot abgelaufen</mu>", "<mu>listing expired</mu>");
        t("mail.reason.ah_zurueck", "<mu>Angebot zurückgenommen</mu>", "<mu>listing taken back</mu>");
        t("mail.reason.ankauf_zurueck", "<mu>vom Ankauf zurück</mu>", "<mu>back from the buy-back</mu>");
        t("mail.reason.auftrag", "<mu>Lieferung für deinen Auftrag</mu>", "<mu>delivery for your order</mu>");
        t("mail.reason.auftrag_zurueck", "<mu>Lieferung zurück</mu>", "<mu>delivery returned</mu>");
        t("mail.reason.belohnung", "<mu>tägliche Belohnung</mu>", "<mu>daily reward</mu>");
        t("mail.reason.laden", "<mu>vom Laden</mu>", "<mu>from a shop</mu>");
        t("mail.reason.stand_geraeumt", "<mu>vom geräumten Marktstand</mu>", "<mu>from your cleared stall</mu>");
    }
}
