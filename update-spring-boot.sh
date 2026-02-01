#!/usr/bin/env bash

handle_error() {
  echo "Error occurred. Resetting changes..."
  git reset --hard "$CURRENT_HEAD"
  exit 1
}

echo "Updating parent version..."
mvn versions:update-parent -DgenerateBackupPoms=false || handle_error
echo "Running tests..."
mvn clean package || handle_error

echo "Update succeeded, committing and pushing..."
git add pom.xml
git commit -m "Update sb"
echo "Update complete"