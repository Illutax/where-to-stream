#!/usr/bin/env bash

cd /home/ubu/docker/w2s

# Defines notify(), reading server and token from $HOME/.config/w2s-update.env. Sourcing it cannot
# fail the run, and neither can any call to notify().
. ./notify.sh

# Everything the sub-scripts print is kept here so a failure notification can carry the last lines
# of it. Cron's mail is the only other copy, and nobody reads that.
RUN_LOG="$(mktemp -t w2s-update.XXXXXX.log)"
trap 'rm -f "$RUN_LOG"' EXIT

run() { # command... -- run it, show its output, and keep a copy in $RUN_LOG
  "$@" 2>&1 | tee -a "$RUN_LOG"
  return "${PIPESTATUS[0]}"
}

handle_error() { # step
  echo "Error occurred. Bailing"
  notify_failed "$1" "$(tail -n 20 "$RUN_LOG")"
  exit 1
}

echo "$(date)"
DEPLOYED_BEFORE=$(git rev-parse --short HEAD)

echo "Trying to upgrade Spring-Boot..."
# 0 = a new Spring Boot version was found, tested and pushed
# 2 = no new version; not an error, and no reason to skip the application update
# * = something actually went wrong
run ./upgrade-spring-boot.sh
case $? in
  0) SPRING_BOOT_STATUS="Spring Boot updated." ;;
  2) SPRING_BOOT_STATUS="No new Spring Boot version." ;;
  *) handle_error "Spring Boot upgrade" ;;
esac
echo "$SPRING_BOOT_STATUS"

# Runs regardless of the above: this is what deploys ordinary commits. Coupling it to a Spring Boot
# release meant the application was only ever redeployed on nights when Spring Boot itself shipped
# something.
echo "Updating application..."
# Same exit-code convention as above: 2 means there was nothing to deploy. A run that changed
# nothing still reports, as the pihole job does -- a silent topic cannot be told apart from a cron
# that stopped firing -- but at `min`, so a night with no news does not buzz anyone's phone.
run ./update-and-restart.sh
case $? in
  0) notify_deployed "$DEPLOYED_BEFORE" "$SPRING_BOOT_STATUS" ;;
  2) echo "Nothing to deploy."
     notify_nothing_new "$DEPLOYED_BEFORE" "$SPRING_BOOT_STATUS" ;;
  *) handle_error "Application update" ;;
esac

echo "All done."
