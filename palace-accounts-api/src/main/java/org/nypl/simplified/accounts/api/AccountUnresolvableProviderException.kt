package org.nypl.simplified.accounts.api

/**
 * An unresolvable provider was specified when trying to create an account.
 */

class AccountUnresolvableProviderException(message: String?) : Exception(message)
