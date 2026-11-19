#!/usr/bin/env bash
set -euo pipefail

UPSTREAM_URL="https://github.com/ivpn/mailx.git"
FORK_URL="${FORK_URL:-${1:-}}"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Initializing a Git repository in $(pwd)"
  git init
  git branch -M main
fi

if git remote get-url upstream >/dev/null 2>&1; then
  git remote set-url upstream "$UPSTREAM_URL"
else
  git remote add upstream "$UPSTREAM_URL"
fi

# Track only the upstream default service branch. This remote is for API review,
# not a merge target for the Android application source tree.
git config remote.upstream.fetch '+refs/heads/main:refs/remotes/upstream/main'
# Prevent accidental pushes to IVPN's official service repository.
git config remote.upstream.pushurl DISABLED

if [[ -n "$FORK_URL" ]]; then
  if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "$FORK_URL"
  else
    git remote add origin "$FORK_URL"
  fi
fi

git fetch --no-tags upstream main

echo
echo "Configured remotes:"
git remote -v

echo
echo "upstream/main is available for review. No merge or rebase was performed."
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Set your own repository as origin when it is ready:"
  echo "  git remote add origin https://github.com/<account>/<repo>.git"
fi
