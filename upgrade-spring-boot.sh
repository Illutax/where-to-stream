#!/usr/bin/env bash
set -uo pipefail

# The commit to fall back to. Captured BEFORE anything is changed, and this line is the whole point:
# handle_error used to reset to an unset $CURRENT_HEAD, so `git reset --hard ""` failed, the pom.xml
# that versions:update-parent had rewritten stayed in the working tree, and the next cron run died
# in update-and-restart.sh on `git pull --rebase` ("got uncommitted changes"). One failed build
# therefore wedged every following night until someone cleaned up by hand.
CURRENT_HEAD="$(git rev-parse HEAD)"
if [ -z "$CURRENT_HEAD" ]; then
  echo "Error: cannot determine current HEAD, refusing to change anything"
  exit 1
fi

handle_error() {
  echo "Error occurred. Resetting to $CURRENT_HEAD..."
  git reset --hard "$CURRENT_HEAD"
  # Belt and braces: a reset does not remove untracked leftovers, and a half-written pom would
  # break the next run just as effectively as the one this replaces.
  git checkout -- pom.xml 2>/dev/null || true
  exit 1
}

get_spring_boot_version() {
  awk '/<parent>/,/<\/parent>/ { if (/<version>/) { gsub(/<version>|<\/version>|[[:space:]]/, ""); print } }' pom.xml
}

## Update
SPRING_BOOT_VERSION=$(get_spring_boot_version)
echo "Updating parent version..."
mvn versions:update-parent -DgenerateBackupPoms=false || handle_error
NEW_SPRING_BOOT_VERSION=$(get_spring_boot_version)

# Check if version changed.
# Exit codes are meaningful to cron.sh:  0 = updated,  2 = nothing to do,  1 = something broke.
# "Nothing to do" used to be a plain 1, which cron.sh could not tell apart from a real failure --
# so on every night without a Spring Boot release the whole chain bailed out and the application
# was never redeployed.
if [ "$SPRING_BOOT_VERSION" = "$NEW_SPRING_BOOT_VERSION" ]; then
  echo "Version didn't change (already at $SPRING_BOOT_VERSION). Nothing to do."
  git reset --hard "$CURRENT_HEAD"
  exit 2
fi

## Test
# Built through the Dockerfile's `verify` stage rather than the host's `mvn clean package`.
# The shipped artifact is built from that same file, off the same `toolchain` stage, so the check
# and the delivery now use one toolchain. Verifying against whatever Node and Maven happen to sit on
# the host meant the nightly check could fail for a reason the actual build never sees -- which is
# how a Node upgrade on the host took the deployment down.
echo "Running tests in the build image..."
docker build . --target verify -t "w2s-verify:latest" || handle_error

## Checking in
echo "Update to $NEW_SPRING_BOOT_VERSION succeeded, committing and pushing..."

git add pom.xml
git commit -m "(Cron) update sb $SPRING_BOOT_VERSION -> $NEW_SPRING_BOOT_VERSION" || handle_error
git pull --rebase || handle_error
echo "pushing..."
git push || handle_error
echo "pushed"
