#!/usr/bin/env bash

git pull --rebase || echo "Error: got uncommitted changes"
docker compose up -d --build
docker compose logs -f
