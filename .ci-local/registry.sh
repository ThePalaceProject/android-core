#!/bin/sh

#------------------------------------------------------------------------
# Utility methods
#

fatal()
{
  echo "registry.sh: fatal: $1" 1>&2
  exit 1
}

info()
{
  echo "registry.sh: info: $1" 1>&2
}

wget -O registry.json "https://registry.palaceproject.io/libraries" ||
  fatal "Could not download registry."

jq -c < registry.json > "../palace-accounts-registry/src/main/resources/org/nypl/simplified/accounts/registry/libraries.json" ||
  fatal "Could not parse and update registry"

rm -f registry.json
