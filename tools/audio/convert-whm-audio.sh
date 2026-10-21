#!/usr/bin/env bash
# Converts the Wim Hof voice-guide source audio (WAV) into Opus-in-WebM raw resources and
# generates the Kotlin clip-duration table.
#
# Usage:
#   convert-whm-audio.sh [--override STEM=<wav-path>]... <voces.zip> <sfx-dir> <raw-res-dir> <WhmClips.kt-path>
#   convert-whm-audio.sh [--override STEM=<wav-path>]... --dry-run <voces.zip> <sfx-dir>
#   convert-whm-audio.sh --self-check
#
# --override STEM=path replaces the zip clip of that stem (e.g. RET_11=/path/re-recorded.wav) so a
# re-recorded clip does not require editing the archive. The stem must exist in the zip. Repeatable.
#
# The inputs are untrusted downloads: the zip is extracted with `unzip -j` (no directory
# components) into a fresh mktemp directory, only `*.wav` members are taken, and every stem is
# validated against ^[A-Z0-9_]+$ before it is used to build an output name. Nothing from the
# inputs is ever executed.
#
# Codec: libopus mono 48 kHz (voice 48 kbps, SFX 64 kbps) in WebM, which Android has played
# since 5.0 (minSdk is 24). Two-pass loudnorm (I=-18, TP=-1.5, LRA=11).
set -euo pipefail

FFMPEG="${FFMPEG:-/opt/homebrew/bin/ffmpeg}"
FFPROBE="${FFPROBE:-/opt/homebrew/bin/ffprobe}"

STEM_REGEX='^[A-Z0-9_]+$'

# "<filename glob after Firefly_audio_>|<raw name suffix>"
SFX_MAP=(
  "Deep_calm_human_inhale*variation1|inhale_slow"
  "Relaxed_human_exhale*variation2|exhale_slow"
  "Strong_full_human_inhale*variation2|inhale_normal"
  "Short_loose_human_exhale*variation3|exhale_normal"
  "Quick_powerful_human_inhale*variation4|inhale_fast"
  "Quick_relaxed_human_exhale*variation4|exhale_fast"
  "Long_complete_human_exhale*variation1|exhale_hold"
  "Very_deep_slow_human_inhale*variation2|inhale_recovery"
  "Soft_single_singing_bowl*variation2|bowl"
)

OVERRIDES=()

die() { echo "error: $*" >&2; exit 1; }

# Fails up front, with the install hint, when a required tool is missing. $FFMPEG/$FFPROBE may be
# a bare command or a path (they default to the Homebrew locations).
require_tools() {
  local tool
  for tool in "$@"; do
    case "$tool" in
      ffmpeg) command -v "$FFMPEG" >/dev/null 2>&1 || die "ffmpeg not found (looked for '$FFMPEG'); install it with 'brew install ffmpeg' or set FFMPEG=<path>" ;;
      ffprobe) command -v "$FFPROBE" >/dev/null 2>&1 || die "ffprobe not found (looked for '$FFPROBE'); it ships with ffmpeg ('brew install ffmpeg') or set FFPROBE=<path>" ;;
      *) command -v "$tool" >/dev/null 2>&1 || die "$tool not found; install it with 'brew install $tool'" ;;
    esac
  done
}

# Prints the raw resource name (without extension) for a voice stem, or fails.
voice_name() {
  local stem="$1"
  [[ "$stem" =~ $STEM_REGEX ]] || return 1
  printf 'whm_vo_%s' "$(printf '%s' "$stem" | tr '[:upper:]' '[:lower:]')"
}

# Replaces the source path of overridden stems in a "<src>|<name>" list read from stdin.
apply_overrides() {
  local line src name entry stem path want replaced
  local -a used=()
  while IFS= read -r line; do
    src="${line%%|*}"
    name="${line##*|}"
    for entry in ${OVERRIDES[@]+"${OVERRIDES[@]}"}; do
      stem="${entry%%=*}"
      path="${entry#*=}"
      want="$(voice_name "$stem")" || die "invalid override stem: $stem"
      if [[ "$name" == "$want" ]]; then
        [[ -f "$path" ]] || die "override file not found: $path"
        src="$path"
        used+=("$stem")
      fi
    done
    printf '%s|%s\n' "$src" "$name"
  done
  for entry in ${OVERRIDES[@]+"${OVERRIDES[@]}"}; do
    stem="${entry%%=*}"
    replaced=0
    for want in ${used[@]+"${used[@]}"}; do [[ "$want" == "$stem" ]] && replaced=1; done
    (( replaced )) || die "override stem not found in the zip: $stem"
  done
}

# Prints the raw resource name for an SFX file name (basename), or fails when unmapped.
sfx_name() {
  local file="$1" entry pattern suffix
  for entry in "${SFX_MAP[@]}"; do
    pattern="${entry%%|*}"
    suffix="${entry##*|}"
    # shellcheck disable=SC2053
    if [[ "$file" == Firefly_audio_${pattern}.wav ]]; then
      printf 'whm_sfx_%s' "$suffix"
      return 0
    fi
  done
  return 1
}

self_check() {
  local failures=0
  expect_eq() { [[ "$1" == "$2" ]] || { echo "FAIL: expected '$2' got '$1'" >&2; failures=$((failures + 1)); }; }
  expect_eq "$(voice_name RESP_01)" "whm_vo_resp_01"
  expect_eq "$(voice_name TIEMPO_030)" "whm_vo_tiempo_030"
  expect_eq "$(voice_name CUENTA_ULTIMA)" "whm_vo_cuenta_ultima"
  voice_name 'bad stem' >/dev/null 2>&1 && { echo "FAIL: space accepted" >&2; failures=$((failures + 1)); }
  voice_name '../x' >/dev/null 2>&1 && { echo "FAIL: traversal accepted" >&2; failures=$((failures + 1)); }
  voice_name 'lower' >/dev/null 2>&1 && { echo "FAIL: lowercase accepted" >&2; failures=$((failures + 1)); }
  expect_eq "${#SFX_MAP[@]}" "9"
  local overridden ovr
  ovr="$(mktemp "${TMPDIR:-/tmp}/whm-override.XXXXXX")"
  OVERRIDES=("RET_11=$ovr")
  overridden="$(printf '/a/RET_10.wav|whm_vo_ret_10\n/a/RET_11.wav|whm_vo_ret_11\n' | apply_overrides)"
  expect_eq "$overridden" "$(printf '/a/RET_10.wav|whm_vo_ret_10\n%s|whm_vo_ret_11' "$ovr")"
  OVERRIDES=("NOPE_1=$ovr")
  (printf '/a/RET_10.wav|whm_vo_ret_10\n' | apply_overrides) >/dev/null 2>&1 &&
    { echo "FAIL: unknown override stem accepted" >&2; failures=$((failures + 1)); }
  OVERRIDES=()
  rm -f "$ovr"
  expect_eq "$(sfx_name 'Firefly_audio_Short_loose_human_exhale,_letting_the_air_go_witho_variation3.wav')" "whm_sfx_exhale_normal"
  expect_eq "$(sfx_name 'Firefly_audio_Soft_single_singing_bowl_chime,_warm,_short_decay_variation2.wav')" "whm_sfx_bowl"
  sfx_name 'Firefly_audio_Unknown_variation1.wav' >/dev/null 2>&1 && { echo "FAIL: unmapped accepted" >&2; failures=$((failures + 1)); }
  require_measurement mi "-23.5" || { echo "FAIL: valid measurement rejected" >&2; failures=$((failures + 1)); }
  require_measurement mi "null" && { echo "FAIL: null measurement accepted" >&2; failures=$((failures + 1)); }
  require_measurement mi "" && { echo "FAIL: empty measurement accepted" >&2; failures=$((failures + 1)); }
  if (( failures > 0 )); then echo "self-check: $failures failure(s)" >&2; exit 1; fi
  echo "self-check: ok"
}

# Extracts the *.wav members of the zip (flattened) into $2 (must be a fresh, empty directory).
extract_zip() {
  unzip -q -j -o "$1" '*.wav' -d "$2"
}

# Aborts when a loudnorm measurement is missing: jq prints "null" (or nothing) for an absent key,
# and feeding that to pass 2 would silently skip the normalisation. $1 label, $2 value.
require_measurement() {
  [[ -n "$2" && "$2" != "null" ]] || return 1
}

# Pass 1 measures, pass 2 applies. $1 in-file, $2 out-file, $3 bitrate.
convert_one() {
  local in="$1" out="$2" bitrate="$3"
  local trim="silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.02"
  local norm="loudnorm=I=-18:TP=-1.5:LRA=11"
  local stats
  stats="$("$FFMPEG" -hide_banner -nostdin -i "$in" \
    -af "${trim},${norm}:print_format=json" -f null - 2>&1 | awk '/^{/{p=1} p{print} /^}/{p=0}')"
  local mi mtp mlra mthresh offset
  mi="$(jq -r '.input_i' <<<"$stats")"
  mtp="$(jq -r '.input_tp' <<<"$stats")"
  mlra="$(jq -r '.input_lra' <<<"$stats")"
  mthresh="$(jq -r '.input_thresh' <<<"$stats")"
  offset="$(jq -r '.target_offset' <<<"$stats")"
  local name value
  for name in mi mtp mlra mthresh offset; do
    value="${!name}"
    require_measurement "$name" "$value" || die "loudnorm measurement '$name' missing for $in (got '${value}')"
  done
  "$FFMPEG" -hide_banner -nostdin -loglevel error -y -i "$in" -map_metadata -1 -ac 1 \
    -af "${trim},${norm}:measured_I=${mi}:measured_TP=${mtp}:measured_LRA=${mlra}:measured_thresh=${mthresh}:offset=${offset}:linear=true,areverse,afade=t=in:d=0.05,areverse" \
    -ar 48000 -c:a libopus -b:a "$bitrate" -vbr on -application audio -f webm "$out"
}

duration_millis() {
  "$FFPROBE" -v error -show_entries format=duration -of csv=p=0 "$1" |
    awk '{ printf "%d", ($1 * 1000) + 0.999 }'
}

# Lists "<source path>|<raw name>" for the 52 voice clips, sorted.
list_voice() {
  local dir="$1" f stem name
  for f in "$dir"/*.wav; do
    stem="$(basename "$f" .wav)"
    name="$(voice_name "$stem")" || die "invalid voice stem: $stem"
    printf '%s|%s\n' "$f" "$name"
  done | sort
}

list_sfx() {
  local dir="$1" f name count=0
  for f in "$dir"/*.wav; do
    [[ -e "$f" ]] || continue
    name="$(sfx_name "$(basename "$f")")" || die "unmapped SFX file: $(basename "$f")"
    printf '%s|%s\n' "$f" "$name"
    count=$((count + 1))
  done | sort
  (( $(find "$dir" -maxdepth 1 -name '*.wav' | wc -l) == ${#SFX_MAP[@]} )) ||
    die "expected ${#SFX_MAP[@]} SFX files in $dir"
}

write_kotlin() {
  local kt="$1" raw="$2"
  {
    echo "package com.pirxhio.affirmity.meditation.wimhof.voice"
    echo
    echo "// GENERATED by tools/audio/convert-whm-audio.sh -- do not edit by hand."
    echo
    echo "/** Duration table for the Wim Hof guide clips (raw resource name == audio id). */"
    echo "object WhmClips {"
    echo "    /** Voice clip id -> duration in milliseconds. */"
    echo "    val voiceMillis: Map<String, Long> = mapOf("
    local f id
    for f in "$raw"/whm_vo_*.webm; do
      id="$(basename "$f" .webm)"
      printf '        "%s" to %sL,\n' "$id" "$(duration_millis "$f")"
    done
    echo "    )"
    echo
    echo "    /** SFX clip id -> duration in milliseconds. */"
    echo "    val sfxMillis: Map<String, Long> = mapOf("
    for f in "$raw"/whm_sfx_*.webm; do
      id="$(basename "$f" .webm)"
      printf '        "%s" to %sL,\n' "$id" "$(duration_millis "$f")"
    done
    echo "    )"
    echo
    echo "    /** Hold time mark in seconds (TIEMPO stem is m:ss, e.g. 100 = 1:00) -> voice clip id. */"
    echo "    val tiempoBySeconds: Map<Int, String> = mapOf("
    for f in "$raw"/whm_vo_tiempo_*.webm; do
      id="$(basename "$f" .webm)"
      local digits="${id##*_}" # e.g. 030 or 100
      local minutes=$((10#${digits:0:1}))
      local seconds=$((10#${digits:1:2}))
      printf '        %d to "%s",\n' $((minutes * 60 + seconds)) "$id"
    done
    echo "    )"
    echo
    echo "    fun durationMillis(id: String): Long? = voiceMillis[id] ?: sfxMillis[id]"
    echo "}"
  } >"$kt"
}

tmp=""
main() {
  if [[ "${1:-}" == "--self-check" ]]; then self_check; return; fi

  local dry=0
  while [[ "${1:-}" == --* ]]; do
    case "$1" in
      --dry-run) dry=1; shift ;;
      --override)
        [[ "${2:-}" == *=* ]] || die "--override needs STEM=path"
        OVERRIDES+=("$2"); shift 2 ;;
      *) die "unknown option: $1" ;;
    esac
  done

  if (( dry )); then
    (( $# == 2 )) || die "usage: $0 --dry-run <voces.zip> <sfx-dir>"
  else
    (( $# == 4 )) || die "usage: $0 <voces.zip> <sfx-dir> <raw-res-dir> <WhmClips.kt-path>"
  fi
  local zip="$1" sfx_dir="$2"
  if (( dry )); then require_tools unzip; else require_tools unzip ffmpeg ffprobe jq; fi
  [[ -f "$zip" ]] || die "zip not found: $zip"
  [[ -d "$sfx_dir" ]] || die "sfx dir not found: $sfx_dir"


  tmp="$(mktemp -d "${TMPDIR:-/tmp}/whm-audio.XXXXXX")"
  trap 'rm -rf "${tmp:-}"' EXIT
  mkdir "$tmp/voice"
  extract_zip "$zip" "$tmp/voice"

  local voice_list sfx_list
  voice_list="$(list_voice "$tmp/voice" | apply_overrides)"
  sfx_list="$(list_sfx "$sfx_dir")"
  local voice_count sfx_count
  voice_count="$(printf '%s\n' "$voice_list" | wc -l | tr -d ' ')"
  sfx_count="$(printf '%s\n' "$sfx_list" | wc -l | tr -d ' ')"
  (( voice_count == 52 )) || die "expected 52 voice clips, found $voice_count"
  (( sfx_count == 9 )) || die "expected 9 sfx clips, found $sfx_count"

  if (( dry )); then
    printf '%s\n' "$voice_list" | awk -F'|' '{ n=split($1, p, "/"); print "voice " p[n] " -> " $2 }'
    printf '%s\n' "$sfx_list" | awk -F'|' '{ n=split($1, p, "/"); print "sfx " p[n] " -> " $2 }'
    echo "dry-run: $voice_count voice + $sfx_count sfx"
    return
  fi

  local raw="$3" kt="$4"
  mkdir -p "$raw" "$(dirname "$kt")"
  local src name
  while IFS='|' read -r src name; do
    convert_one "$src" "$raw/$name.webm" 48k
  done <<<"$voice_list"
  while IFS='|' read -r src name; do
    convert_one "$src" "$raw/$name.webm" 64k
  done <<<"$sfx_list"

  write_kotlin "$kt" "$raw"
  echo "converted $voice_count voice + $sfx_count sfx -> $raw; wrote $kt"
}

main "$@"
