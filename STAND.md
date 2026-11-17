# Stand – MorniSMP

**Was es ist:** Spielmodus „Überleben mit freiem Markt“ des MoinMorni-Netzwerks (Paper-Plugin `MorniSMP`, Java 25,
Paper 26.x), Kennung `mirni`. Wunsch von Philip (15.11.2026): Spawn-Stadt, `/rtp`, Marktplatz, freier Markt unter
Spielern – nur die üblichen Mechaniken, eigene Namen und eigene Optik, nichts für echtes Geld.

Philip (15.11. abends): Die Marktstadt steht nicht mehr in der Oberwelt, sondern in einer **eigenen Spawn-Welt**
`smp_spawn` (leer, immer Tag, kein Wetter, keine Monster, ganz geschützt, „vierte Dimension“). Drei **Reisetore** in der
Stadtmauer – West Oberwelt, Nord Nether, Ost End – bringen per Zufalls-Teleport in die Dimension; Nether und End sind
die normalen Dimensionen (Portale, Festungen, Drache wie Vanilla), `/rtp nether` und `/rtp end` gehen ohne „erst
dort gewesen“.

## Aufbau (`src/main/java/de/moinmorni/smp/`)

- `SmpPlugin` (Start, Befehle, Aufgaben), `Smp` (alle Dienste), `Settings` (config.yml, fehlende Schlüssel aus der
  mitgelieferten Datei).
- `db/`: `Database` (eine SQLite-Datei, `tx` = Sperre + `BEGIN IMMEDIATE`, fachlicher Abbruch = Rollback), `Schema`
  (Schritte hinten anhängen), `Meta`.
- `economy/`: `Money` (Hundertstel, Ein-/Ausgabe de/en), `Bank` (Konten, Kassenbuch `ledger`, Server und Treuhand
  `escrow:…`).
- `market/`: `AuctionHouse`, `Orders` (Treuhand), `Buyback` (Stufen pro Tag), `Shops` (Ladenkisten), `Stalls`
  (Wochenmiete), `Mailbox` (Abholfach), `Notices` (Nachrichten für später), `PriceHistory` (Median), `MarketStats`,
  `Category`, `SearchWords` (deutsch/englisch), `Fees`; `market/ui/`: Menüs und `ShopListener`.
- `town/`: `TownPlan` (Plan der Marktstadt, `VERSION` 5, Marmor-Brunnen mit stillem Wasser in Becken und Schale, Sprudeln als Partikel; Neubau räumt alles über dem Boden, drei Reisetor-Torhäuser mit eigener Optik), `TownBuilder`
  (baut Stück für Stück; in der Spawn-Welt als schwebende Insel mit Fels-Unterseite), `Town` (Laufzeit: Spawn-Welt =
  Stadt, Auslöser, Reisetore), `Boards` (Bestenliste, Standschilder), `Houses`, `Frame`, `Plan`, `Connect`, `Layout`.
- `claims/`: `Claims` (Chunks im Speicher + DB), `ClaimRules` (reine Regeln). `world/`: `Protection` (Spawn-Welt +
  Grundstücke), `TownEvents` (Auslöser, Reisetore, Wiederbeleben in der Stadt, Sturz von der Insel, keine Portale in
  der Spawn-Welt), `VoidGenerator` (leere Welt, Biom Wiese), `GateDecor` (Tor-Bild als Block-Anzeige, große
  Beschriftung, Funken).
- `teleport/`: `Teleports` (Wartezeit, `/back`, `/tpa`), `RandomTeleport` (Menü Oberwelt/Nether/End, Reisetore ohne
  Wartezeit, sicherer Ort, Chunks async, End mit Rückfall auf die Hauptinsel),
  `SafeSpot` (reine Rechnung). `combat/CombatTag`. `bounty/Bounties`. `player/`: `Players`, `DailyReward`,
  `PlayerEvents`. `hud/Sidebar` (eigenes Scoreboard, Teams des Netzwerks werden kopiert).
- `command/`: `Router` + `MoneyCommands`, `TravelCommands`, `ClaimCommand`, `MiscCommands`, `AdminCommand`.
- `net/`: `Network`, `Connected` (einzige Stelle mit `de.moinmorni.api`), `Standalone`, `Reporter` (Statistik).
- `text/`: Texte de/en, `Msg`.
- `test/`: `run.sh` (echter Server), `smp-test.cjs` (zwei Test-Spieler), `fake-network/` (Test-Ersatz des
  Netzwerks, schreibt `calls.log`), `rcon.cjs`.

## Stand (15.11.2026)

- Version 0.1.0 (veröffentlicht, Release v0.1.0).
- Unit-Tests: 45 grün (Geld atomar auch bei gleichzeitigen Überweisungen, Auktions-Kauf-Wettlauf, Treuhand der
  Aufträge inkl. gleichzeitiger Lieferungen, sinkende Ankaufspreise, Gebühren, Grundstücksregeln, sicherer Ort beim
  Zufalls-Teleport, Ablauf der Standmiete, Stadtplan inkl. drei offener Reisetore mit freiem Rückweg und nur stillem Wasser,
  Netzwerk-Statistik, Einstellungen, Texte).
- Echter Test (`FRESH=1 test/run.sh`): Paper 26.1.2 + ViaVersion/ViaBackwards + Netzwerk-Ersatz, normale Welt, zwei
  Test-Spieler (mineflayer): **111/111** – Spawn-Welt (neue Spieler landen dort, Insel schwebt, keine Grundstücke, Marmor-Brunnen nur mit stillem Wasser – auch nach Neubau über fließendes Altwasser),
  drei Reisetore (große Ziel-Schrift + „Reisetor“-Zeile je Tor, frei in der Luft über dem Bogen, mit Größe gegen Wegschneiden im Client, Tor-Bild; hineingehen → Oberwelt/Nether/End auf sicherem Boden, Nether zwischen
  y 32 und 110, End nicht in der Leere; `/spawn` zurück), `/rtp nether` und `/rtp end`, echtes Netherportal in der
  Oberwelt → Nether, Stadt (Blöcke, Bestenliste, Standschilder), Begrüßung, Leiste, /geld, /smpadmin geld, /zahlen,
  /reichste, Auktionshaus verkaufen + kaufen (2 % Gebühr), Auftrag aufgeben + beliefern + abholen, Ankauf (sinkende
  Preise exakt 123 statt 150 ₮), Marktstand über das Schild mieten, Ladenkiste, Kopfgeld, Belohnung, Stadt-Schutz,
  /rtp nah (Ring, sicherer Boden, Abklingzeit), /gs sichern + Schutz + vertrauen + info + aufgeben, /sethome + /spawn
  + /home + /homes, kein PvP auf friedlichem Grundstück, PvP in der Wildnis mit Kampf-Markierung, Flucht im Kampf
  (Kopfgeld an den Gegner), Englisch vom Netzwerk, Netzwerk-Statistik `mirni` und Zustand `running`. Keine Fehler im
  Server-Log.
- Übersetzt gegen Paper-API 26.3 und 26.1.2.

## Händlerhaus (16.11.2026, Version 0.2.0, Branch `claude/shop`, wartet auf Philips Go)

- Server-Shop `/shop` (Philip: „das Beste vom Besten“): `shop/` ohne Bukkit – `Ware`, `Catalog` (Sortiment aus
  `haendler.*`, geprüft: doppelte/unbekannte Sorten weg, Ankauf gedeckelt), `Pricing` (Ankauf ≤ 40 % und echt unter dem
  Kaufpreis, Kosten ohne Überlauf, Tagesgrenzen), `ServerShop` (Kauf in `Database.tx` mit Tagesgrenze + `Bank.move`,
  Kassenbuch `shop` mit „Sorte×Menge“, Verkauf über `Buyback` = Ankauf, Kassenbuch `ankauf`, Umsatz für
  `/smpadmin shop`); Schema-Schritt 2 `shop_daily`. `shop/ui/ShopUi` (Übersicht mit 10 Abteilungen, Suche, Ankauf
  zum Hineinlegen, Inventar verkaufen, Spieler-Markt; Abteilung mit 36 Waren/Seite; Ware-Menü mit grünem Kauf- und
  orangem Verkaufsband, Nachfrage ab 250 ₮), `MerchantNpc` (Händlerin Vroni, Schrift darüber, zwei drehende
  Schaustücke; nicht dauerhaft, alle 10 s geprüft). Händlerhaus ersetzt die Ankaufs-Halle (`TownPlan.VERSION` 6).
  257 Waren, gepresste Blöcke nicht ankaufbar, Saison-Waren (`monate`).
- Geprüft: 62 Unit-Tests (u. a. keine Geld-Schleife über ~35 Rezepte, parallele Käufe), echter Test 147/147 mit Paper
  26.1.2 (Händlerin + Rechtsklick, Abteilung, Kauf, zu wenig Geld, Tagesgrenze, eigene Menge, Nachfrage, Verkauf, alle
  verkaufen, Inventar verkaufen, Kontostand, `/smpadmin shop`, Englisch). Bilder mit Client 26.3 nur lokal unter
  `~/mm-cache/bilder-smp-shop/`.

## Zurück ins Spiel (16./17.11.2026, Version 0.2.1, Branch `claude/zurueck-ins-spiel`, wartet auf Philips Go)

- Philip: „mitten im Spiel nochmal fragen, ob man wirklich zur Lobby will“. Netzwerk-Schnittstelle 2 (byte-gleiche
  Kopie): MorniSMP meldet `playing`, solange jemand im Kampf markiert ist (`CombatTag`: frisch markiert → true; Ablauf,
  Tod, Rauswurf → false); kein `holdSlot` (wer wiederkommt, landet ohnehin dort, wo er war); älteres Netzwerk-Plugin
  (`LinkageError`) → einmal Hinweis im Log. `/smpadmin geld [geben|nehmen|setzen] [spieler] <betrag>`: ohne Art wird
  gegeben, ohne Spieler gilt es einem selbst (`/smpadmin geld 5000` für Owner/Admins zum Testen), wer etwas bekommt,
  erfährt es freundlich (de/en). Test-Netzwerk schreibt `playing`, `ask-lobby`, `hold-slot`; `test/run.sh` mit
  `MEMORY` (Xms 256M); `smp-test.cjs` prüft die neuen Geld-Befehle und `playing` im Kampf. JUnit 6.1.3.
- Geprüft: 62 Unit-Tests, Paper 26.1 übersetzt. Der volle echte Test (`FRESH=1 test/run.sh`) wurde auf dem stark
  ausgelasteten Rechner dreimal vom Speicherwächter beendet (beim Erzeugen der Welten hinter den Reisetoren; bis dahin
  bis zu 39/39 grün). Darum ein Kurztest nur der Änderungen (Paper 26.1.2 + Test-Netzwerk, zwei Test-Spieler, Skript
  nur lokal): **13/13** – `geld geben` mit Nachricht an Anna, `geld Bert 100` ohne „geben“, Hinweis ohne Betrag bzw.
  von der Konsole ohne Spieler, im Spiel `geld 500` / `geld nehmen 600` für sich selbst und `geld setzen Bert 0`;
  Kampf in der Oberwelt → beide `playing true`, nach 15 s beide false, Tod → false, kein holdSlot; Log ohne
  Plugin-Fehler.

## Offen / nicht echt geprüft

- Zurück ins Spiel: voller echter Test mit der neuen Fassung (nur Kurztest, siehe oben); Netzwerk-Repo: nichts im
  Einstellungs-Katalog nötig (MorniSMP hat kein `rueckkehr-sekunden`).

- Händlerhaus: Bedrock-Spieler (Truhen-Menüs, Chat-Eingabe), Preise mit echten Spielern ausbalancieren, Anbindung
  von `haendler.*` an den Einstellungs-Katalog des Panels (Netzwerk-Repo).

- Aussehen der Marktstadt, der schwebenden Insel und der Reisetore im echten Client (Bilder nur lokal) – Philip schaut
  es sich an.
- Bedrock-Spieler (Geyser), Ablauf von Angeboten/Aufträgen/Miete nach echter Zeit (nur in Unit-Tests mit Uhr),
  die Schalter in Auktionshaus/Kontor/Ankauf (gleicher Mechanismus wie das getestete Standschild), Endportal in einer
  Festung und Rückweg aus dem End (landet in der Marktstadt), Drachenkampf (nicht angefasst, also Vanilla), Paper 26.3
  direkt (nur gegen die API übersetzt).
- Einstellungen im Panel des Netzwerks (Einstellungs-Katalog) – gehört ins Netzwerk-Repo.
