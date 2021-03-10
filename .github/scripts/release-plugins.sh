#!/usr/bin/env bash

echo "Wait for a minute for Maven Central to sync and make the artifact available to client plugins"
counter=1
while [ $counter -le 60 ]
do
  printf "."
  sleep 1s
((counter++))
done

git config --global user.email "githubbot@gluonhq.com"
git config --global user.name "Gluon Bot"

CLIENT_MAVEN_REPO_SLUG=gluonhq/client-maven-plugin
CLIENT_GRADLE_REPO_SLUG=gluonhq/client-gradle-plugin

cd $HOME
git clone --depth 5 https://github.com/$CLIENT_MAVEN_REPO_SLUG
git clone --depth 5 https://github.com/$CLIENT_GRADLE_REPO_SLUG

###############################
# 
# Release client-maven-plugin
#
###############################
cd $HOME/client-maven-plugin
# Update Substrate
mvn versions:set-property -Dproperty=substrate.version -DnewVersion=$1 -DgenerateBackupPoms=false
git commit pom.xml -m "Update Substrate version to $1"
# Remove SNAPSHOT
mvn versions:set -DremoveSnapshot
CLIENT_PROJECT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
# Commit and push tag
git commit pom.xml -m "Release $CLIENT_PROJECT_VERSION"
git tag $CLIENT_PROJECT_VERSION
git push https://gluon-bot:$2@github.com/$CLIENT_MAVEN_REPO_SLUG $CLIENT_PROJECT_VERSION

###############################
# 
# Release client-gradle-plugin
#
###############################
cd $HOME/client-gradle-plugin
# Update Substrate version
sed -i "0,/com.gluonhq:substrate:.*/s//com.gluonhq:substrate:$1'/" build.gradle
git commit build.gradle -m "Update Substrate version to $1"
# Remove SNAPSHOT
sed -i "0,/^version '.*'/s//version '$CLIENT_PROJECT_VERSION'/" build.gradle
# Commit and push tag
git commit build.gradle -m "Release $CLIENT_PROJECT_VERSION"
git tag $CLIENT_PROJECT_VERSION
git push https://gluon-bot:$2@github.com/$CLIENT_GRADLE_REPO_SLUG $CLIENT_PROJECT_VERSION