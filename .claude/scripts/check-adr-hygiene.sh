#!/usr/bin/env bash
# ADR hygiene check (ADR-061).
#
# Fails when:
#   - two ADR files in docs/knowledge/adr share the same number;
#   - an ADR file has no status line (**Statut** / **Status**) in its header.
#
# Usage: bash .claude/scripts/check-adr-hygiene.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADR_DIR="$ROOT/docs/knowledge/adr"

if [[ ! -d "$ADR_DIR" ]]; then
  echo "ADR directory not found: $ADR_DIR" >&2
  exit 1
fi

status=0

# --- Unique numbers -------------------------------------------------------
declare -A seen=()
while IFS= read -r file; do
  name="$(basename "$file")"
  number="${name%%-*}-$(echo "$name" | cut -d- -f2)"
  # Skip tombstones: they intentionally keep a renumbered file name.
  if grep -q "RENUMÉROTÉ\|RENUMEROTE\|Tombstone\|tombstone" "$file"; then
    continue
  fi
  if [[ -n "${seen[$number]:-}" ]]; then
    echo "DUPLICATE ADR NUMBER $number: ${seen[$number]} and $name" >&2
    status=1
  else
    seen[$number]="$name"
  fi
done < <(find "$ADR_DIR" -maxdepth 1 -type f -name 'ADR-*.md' | sort)

# --- Mandatory status line ------------------------------------------------
while IFS= read -r file; do
  if ! head -n 12 "$file" | grep -Eq '\*\*(Statut|Status)\*\*'; then
    echo "MISSING STATUS LINE: $(basename "$file")" >&2
    status=1
  fi
done < <(find "$ADR_DIR" -maxdepth 1 -type f -name 'ADR-*.md' | sort)

if [[ $status -eq 0 ]]; then
  echo "ADR hygiene: OK"
fi
exit $status
