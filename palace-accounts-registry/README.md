org.librarysimplified.accounts.registry
===

The `org.librarysimplified.accounts.registry` module provides the
default implementation of the _accounts registry_.

Specifically, it is an implementation of the 
[org.librarysimplified.accounts.registry.api](../palace-accounts-registry-api/README.md)
API.

### Bundled Providers

The code includes a `providers.db` file which is an SQLite database containing the contents of
the current library registry. This database is produced by the `palace-webpub` CLI tool on every
CI build.

Why an SQLite database? The Android runtime is far too slow to load thousands of account providers
on startup from JSON. Loading ~1300 providers from SQLite, in comparison, takes 150ms. Doing the
same from JSON can take over thirty seconds!
