#!/usr/bin/env bash

mvn versions:update-parent -DgenerateBackupPoms=false
mvn clean package
if [[ $? ]]; then
  echo "Update failed, reverting";
  git reset --hard
  exit 1;
fi

echo "Update succeeded, committing and pushing..."
git add pom.xml
git commit -m "Update sb"