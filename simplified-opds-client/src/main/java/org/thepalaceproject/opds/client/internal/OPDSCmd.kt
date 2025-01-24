package org.thepalaceproject.opds.client.internal

import java.util.concurrent.CompletableFuture

internal sealed class OPDSCmd {

  internal val taskFuture: CompletableFuture<Unit> =
    CompletableFuture<Unit>()

  abstract fun execute(context: OPDSCmdContextType)
}
