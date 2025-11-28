org.librarysimplified.accounts.source.spi
===

The `org.librarysimplified.accounts.source.spi` module specifies the
SPI used by implementations that want to contribute to the _accounts
registry_.

Implementations of the `AccountProviderSourceType` interface should
register themselves with `ServiceLoader` and will then be automatically
picked up and used by the accounts registry implementation.

#### See Also

* [org.librarysimplified.accounts.api](../palace-accounts-api/README.md)
* [org.librarysimplified.accounts.registry.api](../palace-accounts-registry-api/README.md)
* [org.librarysimplified.accounts.source.filebased](../palace-accounts-source-filebased/README.md)
* [org.librarysimplified.accounts.source.nyplregistry](../palace-accounts-source-nyplregistry/README.md)
