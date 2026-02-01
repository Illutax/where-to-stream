#!/usr/bin/env bash

git pull --rebase || { echo "Error: got uncommitted changes or pull failed"; exit 1; }
GIT_COMMIT_HASH=$(git rev-parse --short HEAD)

if [ -z "$GIT_COMMIT_HASH" ]; then
  echo "Error: no valid git commit found"
  exit 1
fi
DOCKER_IMAGE_TAG="$(git show -s --format=%cs)_$GIT_COMMIT_HASH"
echo "DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG" > .env
docker build . --build-arg "DOCKER_IMAGE_TAG=$DOCKER_IMAGE_TAG" -t "w2s:$DOCKER_IMAGE_TAG"
docker compose --env-file .env up -d
docker compose logs -f
