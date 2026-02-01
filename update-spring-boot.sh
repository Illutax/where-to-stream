#!/usr/bin/env bash

handle_error() {
  echo "Error occurred. Resetting changes..."
  git reset --hard "$CURRENT_HEAD"
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

# Check if version changed
if [ "$SPRING_BOOT_VERSION" = "$NEW_SPRING_BOOT_VERSION" ]; then
  echo "Version didn't change (already at $SPRING_BOOT_VERSION). Exiting."
  git reset --hard
  exit 0
fi

## Test
echo "Running tests..."
mvn clean package || handle_error

## Checking in
echo "Update to $NEW_SPRING_BOOT_VERSION succeeded, committing and pushing..."

git add pom.xml
git commit -m "Update sb $SPRING_BOOT_VERSION -> $NEW_SPRING_BOOT_VERSION"
git pull --rebase
echo "pushing..."
git push || handle_error
echo "pushed"