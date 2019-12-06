#!/usr/bin/env bash

openssl aes-256-cbc -K $encrypted_da16bb6c74a0_key -iv $encrypted_da16bb6c74a0_iv -in .ci/sonatype.gpg.enc -out sonatype.gpg -d
if [[ ! -s sonatype.gpg ]]
   then echo "Decryption failed."
   exit 1
fi

./gradlew publish --info -PsonatypeUsername=$SONATYPE_USERNAME -PsonatypePassword=$SONATYPE_PASSWORD -Psigning.keyId=$GPG_KEYNAME -Psigning.password=$GPG_PASSPHRASE -Psigning.secretKeyRingFile=sonatype.gpg

# Update version by 1
newVersion=${TRAVIS_TAG%.*}.$((${TRAVIS_TAG##*.} + 1))

# Replace version = TRAVIS_TAG
# with 
# version = newVersion-SNAPSHOT
sed -i -z "0,/version = $TRAVIS_TAG/s//version = $newVersion-SNAPSHOT/" gradle.properties

git commit build.gradle -m "Upgrade version to $newVersion-SNAPSHOT" --author "Github Bot <githubbot@gluonhq.com>"
git push https://gluon-bot:$GITHUB_PASSWORD@github.com/gluonhq/substrate HEAD:master