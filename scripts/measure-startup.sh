#!/usr/bin/env bash
# Measures cold-start TTID and TTFD of the installed app over adb.
#
# Usage: scripts/measure-startup.sh [runs=10] [package=com.pirxhio.affirmity]
#
# Per run: force-stop, clear logcat, `am start -W` (TotalTime = TTID), then wait up to 20s for
# the "ActivityTaskManager: Fully drawn <pkg>/.MainActivity: +XXXms" line (TTFD, emitted by
# reportFullyDrawn). Run 1 is a warm-up and is excluded from the statistics.
# Install the APK first (e.g. the benchmark variant). Works with bash 3.2 (macOS) and stock adb.
set -u

RUNS="${1:-10}"
PKG="${2:-com.pirxhio.affirmity}"
ACTIVITY="$PKG/.MainActivity"
TIMEOUT_S=20

command -v adb >/dev/null 2>&1 || { echo "adb not found" >&2; exit 1; }
adb get-state >/dev/null 2>&1 || { echo "no device connected" >&2; exit 1; }

# Converts an ActivityTaskManager duration ("1s234ms", "567ms", "2s5ms (total ...)") to ms.
to_millis() {
  printf '%s\n' "$1" | awk '{
    s = 0
    if ($0 ~ /^[0-9]+s[0-9]/) { i = index($0, "s"); s = substr($0, 1, i - 1); $0 = substr($0, i + 1) }
    sub(/ms.*/, "")
    print s * 1000 + $0
  }'
}

# Page cache can only be dropped on a rooted device; skip silently otherwise.
can_drop_caches() { [ "$(adb shell id -u 2>/dev/null | tr -d '\r')" = "0" ]; }

TTIDS=""
TTFDS=""
printf "%-5s %10s %10s\n" "run" "TTID(ms)" "TTFD(ms)"

i=1
while [ "$i" -le "$RUNS" ]; do
  adb shell am force-stop "$PKG" >/dev/null 2>&1
  if can_drop_caches; then adb shell "sync; echo 3 > /proc/sys/vm/drop_caches" >/dev/null 2>&1; fi
  adb logcat -c
  sleep 1

  out="$(adb shell am start -W -n "$ACTIVITY" 2>&1 | tr -d '\r')"
  ttid="$(printf '%s\n' "$out" | sed -n 's/^TotalTime: *\([0-9][0-9]*\).*/\1/p' | head -n 1)"

  ttfd=""
  waited=0
  while [ "$waited" -lt "$TIMEOUT_S" ]; do
    line="$(adb logcat -d -s ActivityTaskManager:I 2>/dev/null | tr -d '\r' | grep "Fully drawn $ACTIVITY" | tail -n 1)"
    if [ -n "$line" ]; then
      ttfd="$(printf '%s\n' "$line" | sed -n 's/.*Fully drawn [^ ]*: *+\(.*\)$/\1/p')"
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done

  # "+1s234ms" or "+567ms" -> milliseconds. awk instead of sed alternation: BSD sed lacks `\|`.
  ttfd_ms=""
  if [ -n "$ttfd" ]; then
    ttfd_ms="$(to_millis "$ttfd")"
  fi

  tag=""
  [ "$i" -eq 1 ] && tag="  (warm-up, discarded)"
  printf "%-5s %10s %10s%s\n" "$i" "${ttid:-n/a}" "${ttfd_ms:-timeout}" "$tag"

  if [ "$i" -gt 1 ]; then
    [ -n "$ttid" ] && TTIDS="$TTIDS $ttid"
    [ -n "$ttfd_ms" ] && TTFDS="$TTFDS $ttfd_ms"
  fi
  i=$((i + 1))
done

stats() { # label, values...
  label="$1"; shift
  if [ "$#" -eq 0 ]; then printf "%-6s no samples\n" "$label"; return; fi
  sorted="$(printf '%s\n' "$@" | sort -n)"
  n="$#"
  min="$(printf '%s\n' "$sorted" | head -n 1)"
  max="$(printf '%s\n' "$sorted" | tail -n 1)"
  mid=$(( (n + 1) / 2 ))
  med="$(printf '%s\n' "$sorted" | sed -n "${mid}p")"
  if [ $((n % 2)) -eq 0 ]; then
    med2="$(printf '%s\n' "$sorted" | sed -n "$((mid + 1))p")"
    med=$(( (med + med2) / 2 ))
  fi
  printf "%-6s n=%-3s median=%6s ms  min=%6s ms  max=%6s ms\n" "$label" "$n" "$med" "$min" "$max"
}

echo
echo "Statistics exclude run 1 (warm-up)."
# shellcheck disable=SC2086
stats TTID $TTIDS
# shellcheck disable=SC2086
stats TTFD $TTFDS
