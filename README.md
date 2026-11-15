# Morni-SMP

**MorniSMP** ist der Spielmodus „Überleben mit freiem Markt“ des MoinMorni-Netzwerks (`moinmorni.net`): eine
ummauerte Alpen-Marktstadt als Spawn, ein Auktionshaus, ein Auftragsbrett, eigene Ladenkisten, mietbare Marktstände
und ein Zufalls-Teleport in die Wildnis. Gehandelt wird mit **Talern (₮)** – die gibt es nur im Spiel, nie für echtes
Geld.

Paper-Plugin für **Paper 26.x** (Java 25). Läuft im MoinMorni-Netzwerk (Sprache, Statistik, Zustand) und auch ganz
allein auf einem normalen Paper-Server.

## Was es kann

| Bereich | Befehle | Kurz |
|---|---|---|
| Marktstadt | `/spawn` | Baut sich beim ersten Start selbst bei 0/0 (Marktplatz mit Brunnen und Bestenliste, 16 Marktstände, Auktionshaus, Kontor mit Auftragsbrett, Ankauf, Abholfach, Reisetor, Mauer mit Tortürmen). Geschützt, keine Monster. |
| Geld | `/geld [spieler]`, `/zahlen <spieler> <betrag>`, `/reichste` | Startguthaben, Überweisung (ab 1000 ₮ mit Nachfrage), jede Bewegung steht im Kassenbuch. |
| Auktionshaus | `/ah`, `/ah verkaufen <preis>`, `/ah suche <text>`, `/ah meine` | Abteilungen, Suche (deutsch/englisch), Sortierung, Marktpreis (Median der letzten Verkäufe), 2 % Gebühr, 48 Std., danach ins Abholfach. |
| Abholfach | `/abholen` | Gekauftes, Abgelaufenes, Lieferungen – nichts geht verloren, wenn das Inventar voll ist. |
| Aufträge | `/auftraege`, `/auftraege neu <menge> <preis>` | Kaufaufträge: Geld liegt in der Treuhand, Lieferanten bekommen es Stück für Stück (auch teilweise). |
| Ankauf | `/verkaufen` | Die Stadt kauft Ernte und Mob-Beute; wer mehr als die Tagesmenge bringt, bekommt weniger pro Stück. |
| Ladenkisten | Schild `[Laden]` an einer Kiste | Auf dem eigenen Grundstück oder am gemieteten Stand: verkaufen oder ankaufen, Menge und Preis im Menü. |
| Marktstände | `/stand [mieten\|verlaengern\|kuendigen]` | 16 Stände am Marktplatz, Wochenmiete, Hinweis vor Ablauf, danach geräumt (Sachen ins Abholfach). |
| Grundstücke | `/gs [sichern\|aufgeben\|info\|liste\|vertrauen\|misstrauen\|pvp\|grenzen]` | Ganze Chunks, die ersten 4 gratis, Vertraute, PvP pro Chunk, Schutz gegen Bauen, Kisten, Fluten, Feuer, Explosionen. |
| Reisen | `/rtp [nah\|mittel\|weit\|nether\|end]`, `/home`, `/sethome`, `/delhome`, `/homes`, `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/back` | Sicherer Zufalls-Ort (Chunks im Hintergrund), 3 s Wartezeit (nicht bewegen), Abklingzeit. |
| Kampf | – | 15 s „im Kampf“ nach einem Treffer: keine Teleports, wer geht, verliert seine Sachen. |
| Kopfgeld | `/kopfgeld [spieler betrag]` | Geld liegt in der Treuhand, wer das Ziel besiegt, bekommt alles. |
| Belohnung | `/belohnung` | Einmal am Tag (nach 15 Min. Spielzeit), Serie bis 7 Tage. |
| Anzeige | `/leiste`, `/smp` | Leiste rechts (Geld, Ort, Kampf, Abholfach), Hilfe. |
| Team | `/smpadmin [geld\|bau\|stadt\|neuladen\|stand\|gs\|info]` | Recht `morni.smp.admin` (oder OP). |

Alle Menüs sind Truhen-Menüs mit einfachen Klicks – das geht auch für Bedrock-Spieler (über Geyser).

## Einstellungen

`plugins/MorniSMP/config.yml` (mit Erklärungen): Währung und Startguthaben, Weltgrenzen, Kampf-Sekunden,
Teleport-Wartezeit, Ringe des Zufalls-Teleports, Gebühren, Laufzeiten, Miete, Grundstückspreise, Ankaufspreise und
-stufen, Belohnung, Kopfgeld. Nach Änderungen `/smpadmin neuladen`. Fehlende Schlüssel kommen aus der mitgelieferten
Datei. Die Daten (Konten, Kassenbuch, Angebote, Aufträge, Grundstücke …) liegen in `plugins/MorniSMP/mornismp.db`
(SQLite).

## Netzwerk

Mit dem Netzwerk-Plugin („MoinMorni“) meldet MorniSMP unter der Kennung `mirni`:

- Statistik: `balance` (immer der aktuelle Kontostand in Hundertsteln), `sales`, `purchases`, `orders_filled`,
  `playtime_min` (hochgezählt, alle 30 s)
- Zustand: `running` (bzw. `starting`, während die Stadt gebaut wird) mit Spielerzahl

Ohne Netzwerk: alles auf Deutsch, keine Statistik nach außen.

## Bauen

```
JAVA_HOME=<jdk-25> mvn -q package      # Tests + target/Morni-SMP.jar
```

Jeder Push baut die Jar (GitHub Actions); ein Push auf `main` mit neuer Version in `pom.xml` legt das Release
`v<version>` mit `Morni-SMP.jar` an – das Netzwerk spielt es von dort auf.

## Test mit echtem Server

`test/run.sh` baut das Plugin und einen Test-Ersatz des Netzwerks (`test/fake-network`), richtet einen Paper-26.1.2-
Server ein (offline, Port 25740, RCON 25840, normale Welt, ViaVersion/ViaBackwards), wartet, bis die Marktstadt steht,
und schickt zwei Test-Spieler (mineflayer) durch Stadt, Geld, Auktionshaus, Aufträge, Ankauf, Marktstand, Ladenkiste,
Kopfgeld, Belohnung, Schutz, Zufalls-Teleport, Grundstück, Zuhause, Kampf und Netzwerk-Statistik.

```
FRESH=1 test/run.sh                 # frische Welt + Datenbank
KEEP=1 SKIP_TEST=1 test/run.sh      # nur Server starten
NO_NETWORK=1 test/run.sh            # ohne Netzwerk-Ersatz
```

Erwartet Java 25, eine Paper-Jar und `node_modules` mit mineflayer (Pfade per Umgebungsvariable, siehe Kopf von
`test/run.sh`).

---

Verfasst mit Claude 🤖
