package org.thepalaceproject.opds.client.internal

internal class OPDSCmdShutdown : OPDSCmd() {
  override fun execute(
    context: OPDSCmdContextType
  ) {
    context.shutDown()
    this.taskFuture.complete(Unit)
  }
}
