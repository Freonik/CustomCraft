#!/usr/bin/env bash
# Starts a real Paper server with the freshly built Forgebound.jar, runs a few console commands
# and fails if the plugin doesn't load cleanly. Used by the GitHub Actions build.
set -euo pipefail

MC_VERSION="${MC_VERSION:-1.21.11}"
JAR="$PWD/target/Forgebound.jar"
DIR="$PWD/target/smoke"
LOG="$DIR/console.log"

rm -rf "$DIR"
mkdir -p "$DIR/plugins"
cp "$JAR" "$DIR/plugins/"
cd "$DIR"

# --- download Paper (newer "fill" API first, the old v2 API as a fallback)
URL=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest" \
        | jq -r '.downloads."server:default".url // empty' || true)
if [ -z "$URL" ]; then
  BUILD=$(curl -fsSL "https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds" | jq -r '.builds[-1].build')
  URL="https://api.papermc.io/v2/projects/paper/versions/$MC_VERSION/builds/$BUILD/downloads/paper-$MC_VERSION-$BUILD.jar"
fi
echo "Paper: $URL"
curl -fsSL -o paper.jar "$URL"

# --- a small, fast test world
echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
level-type=minecraft\:flat
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
online-mode=false
server-port=25599
EOF

# --- start the server with the console on a pipe
mkfifo console.in
java -Xms1G -Xmx2G -jar paper.jar --nogui < console.in > "$LOG" 2>&1 &
PID=$!
exec 3> console.in

wait_for() {   # wait_for <pattern> <seconds>
  for _ in $(seq 1 "$2"); do
    if grep -q -- "$1" "$LOG"; then return 0; fi
    if ! kill -0 "$PID" 2>/dev/null; then return 1; fi
    sleep 1
  done
  return 1
}

FAILED=0
fail() { echo "::error::$1"; FAILED=1; }

if ! wait_for 'Done (' 600; then
  fail "The server didn't finish starting"
else
  echo "forge status" >&3
  wait_for 'available\|exists\|forging by' 30 || fail "/forge status printed nothing (commands not registered?)"
  echo "top" >&3
  wait_for 'Most wanted' 30 || fail "/top printed nothing"
  echo "events" >&3
  sleep 3
  # the bundled datapack is loaded ...
  echo "datapack list enabled" >&3
  wait_for 'enabled.*forgebound' 20 || fail "the Forgebound datapack isn't loaded"
  # ... and the Scorched Lands really generate: find one, generate that chunk, check the biome there
  echo "forge scorched" >&3
  if wait_for 'Scorched Lands at' 30; then
    read -r SX SZ < <(grep -o 'Scorched Lands at -\?[0-9]* ~ -\?[0-9]*' "$LOG" | tail -1 | sed -E 's/.* at (-?[0-9]+) ~ (-?[0-9]+)/\1 \2/')
    echo "forceload add $SX $SZ" >&3
    sleep 8
    echo "execute if biome $SX -58 $SZ forgebound:scorched_lands run say SCORCHED_OK" >&3
    wait_for 'SCORCHED_OK' 30 || fail "the Scorched Lands biome wasn't generated at $SX $SZ"
  else
    fail "/forge scorched found nothing"
  fi
  # a guaranteed Scorched Lands area with its Ancient Forge: generate that chunk and check both
  echo "forge ancient" >&3
  if wait_for 'planned: world -\?[0-9]* ~' 20; then
    read -r AX AZ < <(grep -o 'planned: world -\?[0-9]* ~ -\?[0-9]*' "$LOG" | head -1 | sed -E 's/.*world (-?[0-9]+) ~ (-?[0-9]+)/\1 \2/')
    echo "forceload add $AX $AZ" >&3
    sleep 8
    echo "execute if biome $AX -58 $AZ forgebound:scorched_lands run say SURE_SCORCHED_OK" >&3
    wait_for 'SURE_SCORCHED_OK' 30 || fail "the guaranteed Scorched Lands area at $AX $AZ wasn't generated"
    wait_for 'An Ancient Forge generated at world' 20 || fail "the guaranteed Ancient Forge at $AX $AZ wasn't built"
  else
    fail "/forge ancient listed no planned forge"
  fi
  # the End forge (normally after the dragon dies) can be raised and is registered
  echo "forge ancient end" >&3
  wait_for "The End's Ancient Forge rose at" 60 || fail "the End's Ancient Forge didn't rise"
fi

echo "stop" >&3
for _ in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
kill -9 "$PID" 2>/dev/null || true
exec 3>&-

# --- check what the plugin did
grep -q '\[Forgebound\] Loaded [0-9]* weapons' "$LOG" || fail "Forgebound didn't finish enabling"
if grep -n 'Error occurred while enabling Forgebound\|Could not load.*Forgebound\|Forgebound enchantments are missing' "$LOG"; then
  fail "Forgebound failed to load"
fi
# any exception that went through our code, and any warning/error the plugin logged itself
if grep -n 'at cz\.forgebound\.' "$LOG"; then
  fail "An exception went through Forgebound's code"
fi
if grep -nE '(WARN|ERROR)\]: \[Forgebound\]' "$LOG"; then
  fail "Forgebound logged warnings or errors"
fi

echo "----- server console (Forgebound lines) -----"
grep -n 'Forgebound\|forgebound' "$LOG" | head -60 || true
if [ "$FAILED" -ne 0 ]; then
  echo "----- full console -----"
  cat "$LOG"
  exit 1
fi
echo "Smoke test passed."
