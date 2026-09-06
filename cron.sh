#!/usr/bin/env bash

handle_error() {
  echo "Error occurred. Bailing"
  exit 1
}

cd /home/ubu/docker/w2s

echo "$(date)"
echo "Trying to upgrade Spring-Boot..."
# 0 = a new Spring Boot version was found, tested and pushed
# 2 = no new version; not an error, and no reason to skip the application update
# * = something actually went wrong
./upgrade-spring-boot.sh
case $? in
  0) echo "Spring Boot updated." ;;
  2) echo "No new Spring Boot version." ;;
  *) handle_error ;;
esac

# Runs regardless of the above: this is what deploys ordinary commits. Coupling it to a Spring Boot
# release meant the application was only ever redeployed on nights when Spring Boot itself shipped
# something.
echo "Updating application..."
./update-and-restart.sh || handle_error
echo "All done."
