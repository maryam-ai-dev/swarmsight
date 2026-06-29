#!/usr/bin/env bash
#
# Fails if an em dash (U+2014) or en dash (U+2013) appears in any tracked
# source or doc file. House rule: no em dashes or en dashes used as dashes.
#
# Matches by Unicode code point so this script itself stays clean.

set -euo pipefail

cd "$(dirname "$0")/.."

# Directories and files we never scan.
EXCLUDES=(
  ':!.git/*'
  ':!authority/target/*'
  ':!**/__pycache__/*'
  ':!**/.venv/*'
)

# Prefer git-tracked files so build artifacts are ignored. Fall back to a
# find sweep when run outside a git checkout (for example in some CI caches).
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  mapfile -t FILES < <(git ls-files -- . "${EXCLUDES[@]}")
else
  mapfile -t FILES < <(find . \
    -path './.git' -prune -o \
    -path './authority/target' -prune -o \
    -name '__pycache__' -prune -o \
    -name '.venv' -prune -o \
    -type f -print)
fi

if [ "${#FILES[@]}" -eq 0 ]; then
  echo "check-dashes: no files to scan"
  exit 0
fi

# grep -P lets us match the code points without typing the characters here.
if matches=$(grep -nP '[\x{2013}\x{2014}]' "${FILES[@]}" 2>/dev/null); then
  echo "check-dashes: FAIL. Em dash or en dash found:"
  echo "$matches"
  echo
  echo "Replace with a plain hyphen or rewrite the sentence."
  exit 1
fi

echo "check-dashes: OK. No em or en dashes in ${#FILES[@]} files."
exit 0
