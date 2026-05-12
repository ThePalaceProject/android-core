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

#------------------------------------------------------------------------
# Download and verify the CLI.
#

info "Downloading webpub jar"

wget -c https://repo1.maven.org/maven2/org/thepalaceproject/webpub/org.thepalaceproject.webpub.cmdline/1.2.0/org.thepalaceproject.webpub.cmdline-1.2.0-main.jar ||
  fatal "could not download webpub"

sha256sum -c .ci-local/webpub.jar.sha256 ||
  fatal "could not verify webpub.jar"

#------------------------------------------------------------------------
# Bundling registry.
#

rm -f providers.db ||
  fatal "Failed to remove provider database."

java -jar org.thepalaceproject.webpub.cmdline-1.2.0-main.jar \
  bundle-catalogs \
  --start https://registry.palaceproject.io/libraries \
  --output-file providers.db ||
  fatal "Failed to bundle registry"

mv providers.db palace-accounts-registry/src/main/resources/org/nypl/simplified/accounts/registry/providers.db
