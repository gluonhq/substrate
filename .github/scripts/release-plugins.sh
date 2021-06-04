#!/usr/bin/env bash

echo "Wait for a minute for Maven Central to sync and make the artifact available to gluonfx plugins"
counter=1
while [ $counter -le 60 ]
do
  printf "."
  sleep 1s
((counter++))
done

git config --global user.email "githubbot@gluonhq.com"
git config --global user.name "Gluon Bot"

GLUONFX_MAVEN_REPO_SLUG=gluonhq/gluonfx-maven-plugin
GLUONFX_GRADLE_REPO_SLUG=gluonhq/gluonfx-gradle-plugin

cd $HOME
git clone --depth 5 https://github.com/$GLUONFX_MAVEN_REPO_SLUG
git clone --depth 5 https://github.com/$GLUONFX_GRADLE_REPO_SLUG

###############################
# 
# Release gluonfx-maven-plugin
#
###############################
cd $HOME/gluonfx-maven-plugin
# Update Substrate
mvn versions:set-property -Dproperty=substrate.version -DnewVersion=$1 -DgenerateBackupPoms=false
git commit pom.xml -m "Update Substrate version to $1"
# Remove SNAPSHOT
mvn versions:set -DremoveSnapshot
GLUONFX_PROJECT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
# Commit and push tag
git commit pom.xml -m "Release $GLUONFX_PROJECT_VERSION"
git tag $GLUONFX_PROJECT_VERSION
git push https://gluon-bot:$2@github.com/$GLUONFX_MAVEN_REPO_SLUG $GLUONFX_PROJECT_VERSION

###############################
# 
# Release gluonfx-gradle-plugin
#
###############################
cd $HOME/gluonfx-gradle-plugin
# Update Substrate version
sed -i "0,/com.gluonhq:substrate:.*/s//com.gluonhq:substrate:$1'/" build.gradle
git commit build.gradle -m "Update Substrate version to $1"
# Remove SNAPSHOT
sed -i "0,/^version '.*'/s//version '$GLUONFX_PROJECT_VERSION'/" build.gradle
# Commit and push tag
git commit build.gradle -m "Release $GLUONFX_PROJECT_VERSION"
git tag $GLUONFX_PROJECT_VERSION
git push https://gluon-bot:$2@github.com/$GLUONFX_GRADLE_REPO_SLUG $GLUONFX_PROJECT_VERSION