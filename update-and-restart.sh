#!/usr/bin/env bash

# FORCE_UPDATE kann per Env-Variable ODER als erstes Argument gesetzt werden
FORCE_UPDATE="${FORCE_UPDATE:-$1}"

function get_git_hash()
{
  git rev-parse --short HEAD
}

BEFORE_GIT_COMMIT_HASH=$(get_git_hash)
git pull --rebase || { echo "Error: got uncommitted changes or pull failed"; exit 1; }
GIT_COMMIT_HASH=$(get_git_hash)

# Version-Check nur als Guard, wenn FORCE_UPDATE nicht "true" ist
#
# Exit codes are meaningful to cron.sh:  0 = deployed,  2 = nothing to do,  1 = something broke.
# Same convention as upgrade-spring-boot.sh, and for the same reason: without it a quiet night and
# a real deployment look identical from the outside, so neither can be reported honestly.
if [ "$FORCE_UPDATE" != "true" ]; then
  if [ "$BEFORE_GIT_COMMIT_HASH" = "$GIT_COMMIT_HASH" ]; then
    echo "Version didn't change (already at $GIT_COMMIT_HASH). Exiting."
    exit 2
  fi
else
  echo "FORCE_UPDATE=true → Version-Check wird übersprungen."
fi

if [ -z "$GIT_COMMIT_HASH" ]; then
  echo "Error: no valid git commit found"
  exit 1
fi

DOCKER_IMAGE_TAG="$(git show -s --format=%cs)_$GIT_COMMIT_HASH"
echo "DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG" >> .env
# Both steps were previously unchecked, so a failed build or a container that refused to come up
# still ended the script with status 0 -- which is now the difference between "deployed" and
# "failed" in the notification, and was always the difference between a working deployment and a
# silent one.
docker build . --build-arg "DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG" -t "w2s:$DOCKER_IMAGE_TAG" || exit 1
docker compose --env-file .env up -d || exit 1

# Follow the logs only when someone is actually watching. Under cron nothing ever detaches from
# `logs -f`, so the script never returned, cron.sh never reached its last line, and no run could
# ever be reported as finished.
if [ -t 1 ]; then
  docker compose logs -f
else
  docker compose logs --tail=50
fi
