#!/usr/bin/env bash

function get_git_hash()
{
  git rev-parse --short HEAD
}

BEFORE_GIT_COMMIT_HASH=$(get_git_hash)
git pull --rebase || { echo "Error: got uncommitted changes or pull failed"; exit 1; }
GIT_COMMIT_HASH=$(get_git_hash)

# Check if version changed
if [ "$BEFORE_GIT_COMMIT_HASH" = "$GIT_COMMIT_HASH" ]; then
  echo "Version didn't change (already at $GIT_COMMIT_HASH). Exiting."
  exit 0
fi

if [ -z "$GIT_COMMIT_HASH" ]; then
  echo "Error: no valid git commit found"
  exit 1
fi

DOCKER_IMAGE_TAG="$(git show -s --format=%cs)_$GIT_COMMIT_HASH"
echo "DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG" > .env
docker build . --build-arg "DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG" -t "w2s:$DOCKER_IMAGE_TAG"
docker compose --env-file .env up -d
docker compose logs -f

