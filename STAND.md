# Stand – MorniSMP

**Was es ist:** Spielmodus „Überleben mit freiem Markt“ des MoinMorni-Netzwerks (Paper-Plugin `MorniSMP`, Java 25,
Paper 26.x), Kennung `mirni`. Wunsch von Philip (15.11.2026): Spawn-Stadt, `/rtp`, Marktplatz, freier Markt unter
Spielern – nur die üblichen Mechaniken, eigene Namen und eigene Optik, nichts für echtes Geld.

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
- `town/`: `TownPlan` (Plan der Marktstadt, `VERSION`), `TownBuilder` (baut Stück für Stück), `Town` (Laufzeit:
  Bereiche, Auslöser, Reisetor), `Boards` (Bestenliste, Standschilder), `Houses`, `Frame`, `Plan`, `Connect`, `Layout`.
- `claims/`: `Claims` (Chunks im Speicher + DB), `ClaimRules` (reine Regeln). `world/`: `Protection` (Stadt +
  Grundstücke), `TownEvents` (Auslöser, Reisetor, Wiederbeleben in der Stadt).
- `teleport/`: `Teleports` (Wartezeit, `/back`, `/tpa`), `RandomTeleport` (Menü, sicherer Ort, Chunks async),
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
  Zufalls-Teleport, Ablauf der Standmiete, Stadtplan, Netzwerk-Statistik, Einstellungen, Texte).
- Echter Test (`FRESH=1 test/run.sh`): Paper 26.1.2 + ViaVersion/ViaBackwards + Netzwerk-Ersatz, normale Welt, zwei
  Test-Spieler (mineflayer): **78/78** – Stadt steht (Blöcke, Bestenliste, Standschilder), Begrüßung, Leiste, /geld,
  /smpadmin geld, /zahlen, /reichste, Auktionshaus verkaufen + kaufen (2 % Gebühr, Nachricht an Verkäufer), Auftrag
  aufgeben + beliefern + abholen (Treuhand, 2 % Gebühr), Ankauf (Erlös, Nicht-Kaufbares zurück, sinkende Preise exakt
  123 statt 150 ₮), Marktstand über das Schild mieten, Ladenkiste einrichten + Kunde kauft + Kiste geschützt,
  Kopfgeld, Belohnung (einmal am Tag), Stadt-Schutz, /rtp nah (Ring, sicherer Boden, Abklingzeit), /gs sichern +
  Schutz + vertrauen + info + aufgeben, /sethome + /spawn + /home + /homes, kein PvP auf friedlichem Grundstück, PvP
  in der Wildnis mit Kampf-Markierung und gesperrtem /spawn, Flucht im Kampf (Tod + Kopfgeld an den Gegner), Englisch
  vom Netzwerk, Netzwerk-Statistik `mirni` (Kontostand exakt, Verkäufe, Einkäufe, Aufträge) und Zustand `running`.
  Keine Fehler im Server-Log.
- Übersetzt gegen Paper-API 26.3 und 26.1.2.

## Offen / nicht echt geprüft

- Aussehen der Marktstadt im echten Client (Bilder nur lokal) – Philip schaut es sich an.
- Bedrock-Spieler (Geyser), Ablauf von Angeboten/Aufträgen/Miete nach echter Zeit (nur in Unit-Tests mit Uhr),
  Nether/End-Zufalls-Teleport, Reisetor (hineinlaufen) und die Schalter in Auktionshaus/Kontor/Ankauf (gleicher
  Mechanismus wie das getestete Standschild), Paper 26.3 direkt (nur gegen die API übersetzt).
- Einstellungen im Panel des Netzwerks (Einstellungs-Katalog) – gehört ins Netzwerk-Repo.
