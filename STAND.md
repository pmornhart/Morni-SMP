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
- `town/`: `TownPlan` (Plan der Marktstadt, `VERSION` 4, Marmor-Brunnen mit Schale und kleiner Quelle obenauf, drei Reisetor-Torhäuser mit eigener Optik), `TownBuilder`
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

- Version 0.1.0 (Branch `claude/erster-stand`, noch nicht veröffentlicht – wartet auf Philips Go).
- Unit-Tests: 45 grün (Geld atomar auch bei gleichzeitigen Überweisungen, Auktions-Kauf-Wettlauf, Treuhand der
  Aufträge inkl. gleichzeitiger Lieferungen, sinkende Ankaufspreise, Gebühren, Grundstücksregeln, sicherer Ort beim
  Zufalls-Teleport, Ablauf der Standmiete, Stadtplan inkl. drei offener Reisetore mit freiem Rückweg und Brunnen-Quelle, die in die Schale fällt,
  Netzwerk-Statistik, Einstellungen, Texte).
- Echter Test (`FRESH=1 test/run.sh`): Paper 26.1.2 + ViaVersion/ViaBackwards + Netzwerk-Ersatz, normale Welt, zwei
  Test-Spieler (mineflayer): **108/108** – Spawn-Welt (neue Spieler landen dort, Insel schwebt, keine Grundstücke, Marmor-Brunnen),
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

## Offen / nicht echt geprüft

- Aussehen der Marktstadt, der schwebenden Insel und der Reisetore im echten Client (Bilder nur lokal) – Philip schaut
  es sich an.
- Bedrock-Spieler (Geyser), Ablauf von Angeboten/Aufträgen/Miete nach echter Zeit (nur in Unit-Tests mit Uhr),
  die Schalter in Auktionshaus/Kontor/Ankauf (gleicher Mechanismus wie das getestete Standschild), Endportal in einer
  Festung und Rückweg aus dem End (landet in der Marktstadt), Drachenkampf (nicht angefasst, also Vanilla), Paper 26.3
  direkt (nur gegen die API übersetzt).
- Einstellungen im Panel des Netzwerks (Einstellungs-Katalog) – gehört ins Netzwerk-Repo.
