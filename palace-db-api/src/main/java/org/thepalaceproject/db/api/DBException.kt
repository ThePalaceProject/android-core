package org.thepalaceproject.db.api

import org.nypl.simplified.presentableerror.api.PresentableErrorType

class DBException(
  override val message: String,
  override val cause: Throwable
) : Exception(message, cause), PresentableErrorType
