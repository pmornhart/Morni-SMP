#!/usr/bin/env bash
# Echter Test von MorniSMP: baut das Plugin, richtet einen Paper-Server ein (offline, eigener Port, RCON an, normale
# Welt), startet ihn, wartet, bis die Marktstadt steht, schickt zwei Test-Spieler (mineflayer) durch die Abläufe und
# hält den Server wieder an.
#
#   test/run.sh                       # alles
#   KEEP=1 test/run.sh                # Server danach laufen lassen (zum Nachschauen)
#   KEEP=1 SKIP_TEST=1 test/run.sh    # nur Server starten
#   FRESH=1 test/run.sh               # Welt und Datenbank vorher löschen
#
# Vorgaben für die MoinMorni-Sandbox (alles per Umgebungsvariable änderbar):
#   SMP_JAVA      Java 25 (Ordner; sonst die JDK 25 aus dem Cache, sonst JAVA_HOME)
#   PAPER_JAR     Paper 26.1.2 (mineflayer kann bis 26.1)
#   TEMPLATE      ein schon einmal gestarteter Paper-26.1.2-Ordner (libraries/, cache/, versions/ werden kopiert,
#                 damit nichts heruntergeladen werden muss) – optional
#   SMP_NODE_MODULES  node_modules mit mineflayer
#   EXTRA_PLUGINS weitere Jars (Vorgabe: ViaVersion + ViaBackwards aus dem Cache, wie im Netzwerk)
#   PORT / RCON_PORT  25740 / 25840
#   NO_NETWORK=1  ohne Test-Netzwerk (test/fake-network: Sprache, Statistik und Zustand landen in calls.log)
#
# Verfasst mit Claude 🤖
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(dirname "$HERE")"
CACHE="${MM_CACHE:-/home/user/mm-cache}"
CACHED_JDK="$(ls -d "$CACHE"/jdk/jdk-25* 2>/dev/null | head -1 || true)"
JAVA_HOME="${SMP_JAVA:-${CACHED_JDK:-${JAVA_HOME:-}}}"
export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"
PAPER_JAR="${PAPER_JAR:-$CACHE/jars/paper-26.1.2.jar}"
TEMPLATE="${TEMPLATE:-$CACHE/servers/t1}"
SERVER="${SERVER_DIR:-$CACHE/servers/smp-test}"
PORT="${PORT:-25740}"
RCON_PORT="${RCON_PORT:-25840}"
EXTRA_PLUGINS="${EXTRA_PLUGINS-$CACHE/jars/ViaVersion-5.12.2.jar $CACHE/jars/ViaBackwards-5.12.2.jar}"
export NODE_PATH="${SMP_NODE_MODULES:-$CACHE/node/node_modules}${NODE_PATH:+:$NODE_PATH}"

echo "== Bauen"
(cd "$REPO" && mvn -q -B -o -DskipTests package 2>/dev/null || mvn -q -B -DskipTests package)
if [ "${NO_NETWORK:-0}" != "1" ]; then
  (cd "$REPO" && mvn -q -B -o -f test/fake-network/pom.xml package 2>/dev/null || mvn -q -B -f test/fake-network/pom.xml package)
fi

echo "== Server einrichten: $SERVER"
if [ "${FRESH:-0}" = "1" ]; then
  rm -rf "$SERVER/world" "$SERVER/world_nether" "$SERVER/world_the_end" "$SERVER/smp_spawn" "$SERVER/plugins/MorniSMP"
fi
mkdir -p "$SERVER/plugins/MorniSMP"
if [ -d "$TEMPLATE" ] && [ ! -d "$SERVER/libraries" ]; then
  cp -r "$TEMPLATE/libraries" "$TEMPLATE/cache" "$TEMPLATE/versions" "$SERVER/" 2>/dev/null || true
fi
cp "$PAPER_JAR" "$SERVER/paper.jar"
echo "eula=true" > "$SERVER/eula.txt"
RCON_PASS="$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)"
printf '%s' "$RCON_PASS" > "$SERVER/.rcon-pass"
chmod 600 "$SERVER/.rcon-pass"
cat > "$SERVER/server.properties" <<EOF
online-mode=false
enforce-secure-profile=false
server-port=$PORT
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=$RCON_PASS
level-name=world
level-seed=moinmorni
spawn-protection=0
view-distance=5
simulation-distance=4
max-players=20
white-list=false
enforce-whitelist=false
motd=MorniSMP Test
difficulty=peaceful
EOF
# Testwerte: Belohnung sofort, kurze Abklingzeit des Zufalls-Teleports (5 s).
cat > "$SERVER/plugins/MorniSMP/config.yml" <<'EOF'
belohnung:
  mindest-minuten: 0
zufallsteleport:
  abklingzeit-sekunden: 5
EOF
rm -f "$SERVER/plugins/"Morni-SMP*.jar "$SERVER/plugins/MoinMorni-FakeNetwork.jar"
# Test-Netzwerk (NO_NETWORK=1: MorniSMP läuft allein): Cleo spricht Englisch, Meldungen landen in calls.log.
if [ "${NO_NETWORK:-0}" != "1" ]; then
  mkdir -p "$SERVER/plugins/MoinMorni"
  printf 'lang.Cleo=en\n' > "$SERVER/plugins/MoinMorni/fake.properties"
  rm -f "$SERVER/plugins/MoinMorni/calls.log"
  cp "$REPO/test/fake-network/target/MoinMorni-FakeNetwork.jar" "$SERVER/plugins/"
fi
cp "$REPO/target/Morni-SMP.jar" "$SERVER/plugins/"
for jar in $EXTRA_PLUGINS; do
  [ -f "$jar" ] && cp "$jar" "$SERVER/plugins/"
done

echo "== Server starten (Port $PORT, RCON $RCON_PORT)"
cd "$SERVER"
: > console.log
nice -n 15 "$JAVA" -Xms512M -Xmx2G -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar paper.jar --nogui < /dev/null > console.log 2>&1 &
PID=$!
echo "$PID" > "$SERVER/.pid"
stop_server() {
  if kill -0 "$PID" 2>/dev/null; then
    node "$HERE/rcon.cjs" "127.0.0.1:$RCON_PORT" "$SERVER/.rcon-pass" stop >/dev/null 2>&1 || kill "$PID" || true
    for _ in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$PID" 2>/dev/null || true
  fi
}
if [ "${KEEP:-0}" != "1" ]; then
  trap stop_server EXIT
fi

READY="Marktstadt fertig|Marktstadt: baue|MorniSMP läuft"
for _ in $(seq 1 600); do
  if grep -q "Marktstadt fertig" console.log; then break; fi
  if grep -q "Done (" console.log && grep -q "MorniSMP läuft" console.log && ! grep -q "Marktstadt: baue" console.log; then
    break # stand schon
  fi
  if ! kill -0 "$PID" 2>/dev/null; then echo "Server ist abgestürzt:"; tail -40 console.log; exit 1; fi
  sleep 1
done
grep -E "$READY|Marktstadt: Boden" console.log || true

if [ "${SKIP_TEST:-0}" = "1" ]; then
  echo "Server läuft (PID $PID) – anhalten mit: node $HERE/rcon.cjs 127.0.0.1:$RCON_PORT $SERVER/.rcon-pass stop"
  exit 0
fi

echo "== Test-Spieler"
STATUS=0
NO_NETWORK="${NO_NETWORK:-0}" node "$HERE/smp-test.cjs" 127.0.0.1 "$PORT" "127.0.0.1:$RCON_PORT" "$SERVER/.rcon-pass" "$SERVER" || STATUS=$?

echo "== Fehler im Server-Log:"
if grep -qE "ERROR|Exception" console.log; then
  grep -E "ERROR|Exception" console.log | head -20
else
  echo "keine"
fi
exit $STATUS
