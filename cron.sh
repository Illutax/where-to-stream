#!/usr/bin/env bash

handle_error() {
  echo "Error occurred. Bailing"
  exit 1
}

echo "Trying to upgrade Spring-Boot..."
./upgrade-spring-boot.sh || handle_error
echo "Updating application..."
./update-and-restart.sh || handle_error
echo "All done."