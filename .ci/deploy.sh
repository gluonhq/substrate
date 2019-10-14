#!/usr/bin/env bash

openssl aes-256-cbc -K $encrypted_55b8fcde0fad_key -iv $encrypted_55b8fcde0fad_iv -in .ci/sonatype.gpg.enc -out sonatype.gpg -d
if [[ ! -s sonatype.gpg ]]
   then echo "Decryption failed."
   exit 1
fi

./gradlew publish --info -PsonatypeUsername=$SONATYPE_USERNAME -PsonatypePassword=$SONATYPE_PASSWORD -Psigning.keyId=$GPG_KEYNAME -Psigning.password=$GPG_PASSPHRASE -Psigning.secretKeyRingFile=sonatype.gpg