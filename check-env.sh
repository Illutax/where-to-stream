#!/usr/bin/env bash
# Compare a deployment .env against .env.example.
#
# Names only -- this never prints a value, so its output is safe to paste into a chat or a ticket.
# The one value comparison it does make is "is this still literally what .env.example suggested",
# and even then it reports the name alone.
#
# Usage: ./check-env.sh [example] [actual]     (defaults: .env.example .env)
set -euo pipefail

example="${1:-.env.example}"
actual="${2:-.env}"

for f in "$example" "$actual"; do
  [ -f "$f" ] || { echo "missing file: $f" >&2; exit 2; }
done

names() { grep -oE '^[A-Z][A-Z0-9_]*=' "$1" | tr -d '=' | sort -u; }
value() { sed -n "s/^$2=//p" "$1" | head -1; }

missing=$(comm -23 <(names "$example") <(names "$actual"))
extra=$(comm -13 <(names "$example") <(names "$actual"))

blank=""; unchanged=""
while read -r name; do
  [ -n "$name" ] || continue
  a=$(value "$actual" "$name")
  e=$(value "$example" "$name")
  if [ -z "$a" ]; then
    blank="$blank $name"
  elif [ "$a" = "$e" ] && [ -n "$e" ]; then
    unchanged="$unchanged $name"
  fi
done < <(comm -12 <(names "$example") <(names "$actual"))

status=0
report() { # label, items, fatal
  [ -n "$2" ] || return 0
  printf '\n%s\n' "$1"
  printf '  %s\n' $2
  [ "$3" = "fatal" ] && status=1
  return 0
}

report "Documented but not set (the app falls back to its default, which may be none):" "$missing" fatal
report "Set but blank:" "$blank" fatal
report "Still at the value .env.example suggests -- change these before this is reachable:" "$unchanged" fatal
report "Set but not documented in .env.example (EnvExampleIsWiredUpTest only checks the other direction):" "$extra" warn

if [ "$status" = 0 ] && [ -z "$extra" ]; then
  echo "$actual matches $example: $(names "$example" | wc -l) variables, all set."
fi
exit "$status"
