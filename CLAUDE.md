# MorniSMP (Morni-SMP)

@STAND.md

Spielmodus „Überleben mit freiem Markt“ des MoinMorni-Netzwerks (Paper-Plugin `MorniSMP`, Java 25, Paper 26.x).
Kennung im Netzwerk: **`mirni`** (angezeigt „MorniSMP“). Baut seine Marktstadt selbst aus dem Code, läuft im Netzwerk
und auch ganz allein.

## Regeln für dieses Repo

- Repo ist **öffentlich**: keine Geheimnisse, Tokens, Passwörter oder privaten Daten committen (auch keine
  Server-Ordner, Welten, Datenbanken oder RCON-Passwörter aus Tests).
- Texte im Spiel auf **Deutsch (Standard) und Englisch**, freundlich, keine Fachbegriffe ohne Erklärung. Alle Texte
  stehen in `text/TextsGeneral.java`, `TextsMarket.java`, `TextsWorld.java` (MiniMessage); `TextsTest` prüft, dass jeder
  benutzte Schlüssel existiert und beide Sprachen dieselben Platzhalter haben. Befehls-Beispiele mit spitzen Klammern
  escapen (`\\<spieler>`).
- **Keine fremden Marken, Namen, Texte, Ränge oder Grafiken** anderer Server (auch nicht von bekannten Economy-SMPs):
  nur die üblichen Mechaniken, eigene Namen, eigene Optik, eigene Bauten.
- **Nichts für echtes Geld**, kein Pay-to-win: Taler gibt es nur im Spiel (Start, Belohnung, Ankauf, Handel).
- Die Netzwerk-Schnittstelle `src/api/java/de/moinmorni/api/MoinMorni.java` ist eine **unveränderte Kopie** aus
  `pmornhart/moinmorni.net` (`plugins/moinmorni-network/src/main/java/de/moinmorni/api/MoinMorni.java`). Nie hier
  ändern – ändert sie sich dort, 1:1 ersetzen. Sie wird nur mitübersetzt und kommt **nicht** in die Jar. Nur
  `net/Connected.java` darf sie benutzen (`softdepend: [MoinMorni]`, ohne Netzwerk läuft `net/Standalone`).
- Geld nur über `economy/Bank` bewegen (in `Database.tx`): jede Bewegung steht im Kassenbuch, Konten werden nie
  negativ. Geld in Hundertstel-Talern (`long`).
- Ändert sich die Marktstadt: `TownPlan.VERSION` erhöhen – dann baut sie sich beim nächsten Start neu (Stände werden
  vorher geräumt). Außerhalb der Stadt wird nie etwas verändert.
- Neue Tabellen/Spalten nur als neuer Schritt hinten in `db/Schema.STEPS` (alte nie ändern).
- „Alles im Panel“: Einstellungen nur in der `config.yml` (keine Einstellungs-Befehle); die Anbindung an den
  Einstellungs-Katalog gehört ins Netzwerk-Repo.
- „Verfasst mit Claude 🤖“ als letzte Zeile in Commit-Messages (durch eine Leerzeile getrennt), in PRs, Issues und
  Release-Notes; einmal als Fußzeile im README.
- Commits auf Deutsch mit Stichpunkten `- Neu:` / `- Behoben:` / `- Verbessert:` / `- Entfernt:`.
- Neue Version: `<version>` in `pom.xml` erhöhen und `docs/releases/v<version>.md` anlegen; ein Push auf `main` baut
  das Release `v<version>` mit `Morni-SMP.jar` – das Netzwerk spielt es auf.
- Vor jeder Veröffentlichung (Merge nach `main`) Philips Go abwarten.
- Nach Änderungen am Spielablauf echt testen (`test/run.sh`, siehe README); Bilder mit echten Spiel-Texturen nur
  lokal, nie ins Repo.
- `STAND.md` nach jedem größeren Schritt aktualisieren.

## Bauen und testen

```
JAVA_HOME=<jdk-25> mvn -q -DskipTests package   # Jar: target/Morni-SMP.jar
JAVA_HOME=<jdk-25> mvn test                     # Unit-Tests
JAVA_HOME=<jdk-25> mvn -q -Ppaper-26.1 compile  # übersetzt auch gegen Paper 26.1
FRESH=1 test/run.sh                             # echter Server + zwei Test-Spieler
```
