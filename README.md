# Morni-SMP

**MorniSMP** ist der Spielmodus „Überleben mit freiem Markt“ des MoinMorni-Netzwerks (`moinmorni.net`): eine
ummauerte Alpen-Marktstadt als Spawn – schwebend in einer eigenen Welt, einer Art vierter Dimension –, ein
Auktionshaus, ein Auftragsbrett, eigene Ladenkisten, mietbare Marktstände und drei Reisetore, die per
Zufalls-Teleport in Oberwelt, Nether und End führen. Gehandelt wird mit **Talern (₮)** – die gibt es nur im Spiel, nie für echtes
Geld.

Paper-Plugin für **Paper 26.x** (Java 25). Läuft im MoinMorni-Netzwerk (Sprache, Statistik, Zustand) und auch ganz
allein auf einem normalen Paper-Server.

## Was es kann

| Bereich | Befehle | Kurz |
|---|---|---|
| Spawn-Welt | `/spawn` | Eigene leere Welt `smp_spawn` (immer Tag, kein Wetter, keine Monster, ganz geschützt). Die Marktstadt baut sich beim ersten Start dort als schwebende Insel (Marktplatz mit Marmor-Brunnen (Becken und Schale mit stillem Wasser, Sprudeln und Tropfen als Partikel, Gold, Laternen) und Bestenliste, 16 Marktstände, Auktionshaus, Kontor mit Auftragsbrett, Ankauf, Abholfach, Mauer mit Tortürmen). Neue Spieler, `/spawn` und wer ohne Bett stirbt, landen hier; wer von der Insel fällt, steht wieder auf dem Marktplatz. |
| Reisetore | durch das Tor gehen | Drei Tore in der Stadtmauer: **West = Oberwelt** (Moos, Eiche), **Nord = Nether** (Schwarzstein, weinender Obsidian, echtes Portal-Wabern), **Ost = End** (Endstein, Purpur). Hindurchgehen = Zufalls-Teleport in diese Dimension auf einen sicheren Platz. Nether und End sind die normalen Dimensionen: Netherportale, Festungen, Endportal und Drachenkampf wie gewohnt. |
| Geld | `/geld [spieler]`, `/zahlen <spieler> <betrag>`, `/reichste` | Startguthaben, Überweisung (ab 1000 ₮ mit Nachfrage), jede Bewegung steht im Kassenbuch. |
| Auktionshaus | `/ah`, `/ah verkaufen <preis>`, `/ah suche <text>`, `/ah meine` | Abteilungen, Suche (deutsch/englisch), Sortierung, Marktpreis (Median der letzten Verkäufe), 2 % Gebühr, 48 Std., danach ins Abholfach. |
| Abholfach | `/abholen` | Gekauftes, Abgelaufenes, Lieferungen – nichts geht verloren, wenn das Inventar voll ist. |
| Aufträge | `/auftraege`, `/auftraege neu <menge> <preis>` | Kaufaufträge: Geld liegt in der Treuhand, Lieferanten bekommen es Stück für Stück (auch teilweise). |
| Händlerhaus | `/shop [abteilung \| suche <text> \| verkaufen]` | Server-Shop am Marktplatz mit Händlerin Vroni: 261 Waren in zehn Abteilungen (Baublöcke, Holz & Pflanzen, Erze & Edelsteine, Farben & Deko, Essen & Ernte, Werkzeug & Waffen, Tränke & Zauber, Redstone & Technik, Mob-Beute, Besonderes & Saison), Suche deutsch/englisch, Kauf- und Ankaufspreis im Tooltip. Kaufen 1 … 64, Stapel, „so viel wie passt“, eigene Menge; verkaufen bis „alle“, ganzes Inventar mit Übersicht; Nachfrage ab 250 ₮; Tagesgrenzen und Saison-Waren; Preise mit Kurs der Börse, Knopf „Kursverlauf“. |
| Ankauf | `/verkaufen [hand \| alles]` | Teil des Händlerhauses: Sachen hineinlegen und verkaufen. Volle Preise bis zur Tagesmenge, danach weniger pro Stück. `hand`: sofort verkaufen – erst an Kaufaufträge, die mehr zahlen als Vroni, der Rest an Vroni; `alles`: ganzes Inventar mit Übersicht. |
| Börse | `/boerse [ware]`, `/wert [alles]` | Jede Ware des Händlerhauses hat einen Kurs nach Angebot und Nachfrage (viel verkauft → Vroni zahlt weniger, viel gekauft → sie verlangt mehr, mit der Zeit zurück zum Grundpreis). Menü mit Steigern, Fallenden, Erzen, Verlauf als Säulen (24 Std./7 Tage) und Spielermarkt; Kurstafel am Händlerhaus; `/wert` zeigt, was das in der Hand bei Vroni und unter Spielern wert ist. |
| Ladenkisten | Schild `[Laden]` an einer Kiste | Auf dem eigenen Grundstück oder am gemieteten Stand: verkaufen oder ankaufen, Menge und Preis im Menü. |
| Marktstände | `/stand [mieten\|verlaengern\|kuendigen]` | 16 Stände am Marktplatz, Wochenmiete, Hinweis vor Ablauf, danach geräumt (Sachen ins Abholfach). |
| Grundstücke | `/gs [sichern\|aufgeben\|info\|liste\|vertrauen\|misstrauen\|pvp\|grenzen]` | Ganze Chunks in Oberwelt, Nether und End (nicht in der Spawn-Welt), die ersten 4 gratis, Vertraute, PvP pro Chunk, Schutz gegen Bauen, Kisten, Fluten, Feuer, Explosionen. |
| Reisen | `/rtp [nah\|mittel\|weit\|nether\|end]`, `/home`, `/sethome`, `/delhome`, `/homes`, `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/back` | Menü: Oberwelt (Nah/Mittel/Weit), Nether, End. Sicherer Zufalls-Ort (Chunks im Hintergrund; Nether zwischen y 32 und 110 unter dem Dach, nie auf Lava; End auf den äußeren Inseln, sonst Hauptinsel, nie in die Leere), 3 s Wartezeit (nicht bewegen), Abklingzeit. Zuhause in allen Dimensionen außer der Spawn-Welt. |
| Kampf | – | 15 s „im Kampf“ nach einem Treffer: keine Teleports, wer geht, verliert seine Sachen. |
| Kopfgeld | `/kopfgeld [spieler betrag]` | Geld liegt in der Treuhand, wer das Ziel besiegt, bekommt alles. |
| Belohnung | `/belohnung` | Einmal am Tag (nach 15 Min. Spielzeit), Serie bis 7 Tage. |
| Anzeige | `/leiste`, `/smp` | Leiste rechts (Geld, Ort, Kampf, Abholfach), Hilfe. |
| Team | `/smpadmin [geld\|bau\|stadt\|neuladen\|stand\|gs\|info\|shop\|boerse]` | Recht `morni.smp.admin` (oder OP). `boerse`: Kurse ansehen, zurücksetzen, von Hand setzen. |

Alle Menüs sind Truhen-Menüs mit einfachen Klicks – das geht auch für Bedrock-Spieler (über Geyser).

## Einstellungen

`plugins/MorniSMP/config.yml` (mit Erklärungen): Währung und Startguthaben, Spawn-Welt (Name, Höhe der Insel),
Ring des Reisetors zur Oberwelt, Weltgrenzen, Kampf-Sekunden,
Teleport-Wartezeit, Ringe des Zufalls-Teleports, Gebühren, Laufzeiten, Miete, Grundstückspreise, Ankaufsstufen,
Belohnung, Kopfgeld, das ganze Händlerhaus (`haendler.*`: an/aus, Abteilungen, Preis-Faktor je Abteilung
`faktoren`, Waren mit `kauf`, `verkauf`, `tag`, `kauf-tag`, `monate`, Nachfrage-Grenze, `verkauf-max-prozent`) und die
Börse (`boerse.*`: an/aus, `halbieren-nach`, `erholung-stunden`, `min-prozent`, `max-prozent`). Nach Änderungen `/smpadmin neuladen`. Fehlende Schlüssel kommen aus der mitgelieferten
Datei. Die Daten (Konten, Kassenbuch, Angebote, Aufträge, Grundstücke …) liegen in `plugins/MorniSMP/mornismp.db`
(SQLite).

## Händlerhaus und freier Markt

Die Händlerin ist Obergrenze und Untergrenze zugleich: Sie verkauft teuer (wer es billiger will, kauft bei Spielern im
Auktionshaus, an Ladenkisten oder per Auftrag) und kauft billig an – höchstens 40 % ihres Verkaufspreises
(`haendler.verkauf-max-prozent`), immer echt weniger, als sie verlangt. Volle Ankaufspreise gibt es pro Spieler und Tag
nur bis zur Tagesmenge der Ware, danach die Stufen aus `ankauf.stufen`. Gepresste Blöcke (Eisenblock, Heuballen …)
kauft sie nicht an, damit niemand die Tagesmengen umgeht. Die Tests prüfen, dass Kaufen und gleich wieder Verkaufen –
auch über Werkbank oder Ofen – nie Gewinn bringt und dass gleichzeitige Käufe nie mehr Geld abbuchen, als da ist.
Jeder Kauf steht im Kassenbuch als `shop`, jeder Verkauf als `ankauf` (mit Sorte × Menge); `/smpadmin shop` zeigt den
Umsatz des Tages.

Erze und Edelsteine sind kostbar (Diamant 3.200 ₮, Vroni zahlt 800 ₮; Netheritbarren 85.000 ₮), und was aus Erz
gemacht ist, kostet mindestens sein Erz. Der **Schleifen-Wächter** rechnet über 200 Umwege (Werkbank, Ofen, Abbauen) und
senkt jeden Ankaufspreis, der sonst „kaufen, umwandeln, verkaufen“ lohnend machte – auch wenn die Preise einer Abteilung
mit `haendler.faktoren` gedreht werden. Smaragde kauft Vroni nicht an (Dorfbewohner geben sie zu billig her); was
Dorfbewohner für Smaragde hergeben, bringt über Vroni höchstens ein paar Taler am Tag (Tagesmenge, Ankaufstufen, Kurs).

**Börse:** Der Kurs einer Ware sinkt, wenn die Stadt mehr an Vroni verkauft als bei ihr kauft, und steigt umgekehrt
(nach `halbieren-nach` Tagesmengen halber bzw. doppelter Preis), mit der Halbwertszeit `erholung-stunden` geht er zum
Grundpreis zurück. Vroni zahlt nie mehr als den Grundpreis und verlangt nie weniger – so bringt Warten auf einen
besseren Kurs bei ihr nie Gewinn, und alles, was der Schleifen-Wächter prüft, gilt zu jedem Kurs. Der Verlauf liegt
stündlich in der Datenbank (30 Tage).

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
und schickt zwei Test-Spieler (mineflayer) durch Spawn-Welt, alle drei Reisetore, `/rtp nether` und `/rtp end`, Stadt, Händlerin, Geld, Auktionshaus, Aufträge, Ankauf, Händlerhaus (`/shop`), Marktstand, Ladenkiste,
Kopfgeld, Belohnung, Schutz, Zufalls-Teleport, Grundstück, Zuhause, Kampf, Börse (`/wert`, `/verkaufen hand` an
Kaufauftrag und Vroni, `/boerse` mit Verlauf, Kurstafel, Kurs von Hand) und Netzwerk-Statistik.

```
FRESH=1 test/run.sh                 # frische Welt + Datenbank
KEEP=1 SKIP_TEST=1 test/run.sh      # nur Server starten
NO_NETWORK=1 test/run.sh            # ohne Netzwerk-Ersatz
LANGSAM=3 test/run.sh               # ausgelasteter Rechner: alle Wartezeiten der Test-Spieler ×3
```

Erwartet Java 25, eine Paper-Jar und `node_modules` mit mineflayer (Pfade per Umgebungsvariable, siehe Kopf von
`test/run.sh`).

---

Verfasst mit Claude 🤖
