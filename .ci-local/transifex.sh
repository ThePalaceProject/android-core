#!/bin/sh

#------------------------------------------------------------------------
# Utility methods
#

fatal()
{
  echo "transifex.sh: fatal: $1" 1>&2
  exit 1
}

info()
{
  echo "transifex.sh: info: $1" 1>&2
}

if [ -z "${TRANSIFEX_TOKEN}" ]
then
  fatal "TRANSIFEX_TOKEN is not defined"
fi
if [ -z "${TRANSIFEX_SECRET}" ]
then
  fatal "TRANSIFEX_SECRET is not defined"
fi

#------------------------------------------------------------------------
# Download and verify transifex.
#

info "Downloading Transifex jar"

wget -c https://github.com/transifex/transifex-java/releases/download/1.2.1/transifex.jar ||
  fatal "could not download Transifex"

sha256sum -c .ci-local/transifex.sha256 ||
  fatal "could not verify transifex.jar"

#------------------------------------------------------------------------
# Apply transifex to the project's string resources.
#

java .ci-local/TransifexFindResources.java */src ||
  fatal "could not merge string resources"

TRANSIFEX_PUSH_ARGS="--verbose"
TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --token=${TRANSIFEX_TOKEN}"
TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --secret=${TRANSIFEX_SECRET}"
TRANSIFEX_PUSH_ARGS="${TRANSIFEX_PUSH_ARGS} --file=Transifex.xml"

info "Uploading Transifex strings"

java -jar transifex.jar push ${TRANSIFEX_PUSH_ARGS} ||
  fatal "could not upload transifex strings"

TRANSIFEX_PULL_ARGS="--token=${TRANSIFEX_TOKEN}"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --dir=simplified-app-palace/src/main/assets"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --locales=en"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --locales=es"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --locales=fr"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --locales=de"
TRANSIFEX_PULL_ARGS="${TRANSIFEX_PULL_ARGS} --locales=it"

info "Downloading Transifex strings"

java -jar transifex.jar pull ${TRANSIFEX_PULL_ARGS} ||
  fatal "could not download transifex strings"
