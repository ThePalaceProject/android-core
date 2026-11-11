package org.nypl.simplified.feeds.api

/**
 * Functions to transform lists of acquisitions into lists of linearized acquisition paths.
 */
object FeedAcquisitionPaths {
  private fun indirectPathsInner(
    elementStack: MutableList<FeedAcquisitionPathElement>,
    output: MutableList<FeedAcquisitionPath>,
    source: FeedAcquisition,
    indirect: FeedIndirectAcquisition,
  ) {
    val newPathElement = FeedAcquisitionPathElement(indirect.type, null, indirect.properties)
    if (indirect.indirectAcquisitions.isEmpty()) {
      val pathElements = mutableListOf<FeedAcquisitionPathElement>()
      pathElements.addAll(elementStack)
      pathElements.add(newPathElement)
      output.add(FeedAcquisitionPath(source, pathElements.toList()))
    } else {
      elementStack.add(newPathElement)
      for (subIndirect in indirect.indirectAcquisitions) {
        indirectPathsInner(elementStack, output, source, subIndirect)
      }
      elementStack.removeAt(elementStack.size - 1)
    }
  }

  private fun indirectPaths(
    source: FeedAcquisition,
    indirect: FeedIndirectAcquisition,
  ): List<FeedAcquisitionPath> {
    val paths = mutableListOf<FeedAcquisitionPath>()
    indirectPathsInner(mutableListOf(), paths, source, indirect)
    return paths
  }

  private fun acquisitionPaths(source: FeedAcquisition): List<FeedAcquisitionPath> {
    val paths = mutableListOf<FeedAcquisitionPath>()
    if (source.indirectAcquisitions.isEmpty()) {
      paths.add(
        FeedAcquisitionPath(
          source,
          listOf(FeedAcquisitionPathElement(source.type, source.uri, source.properties)),
        ),
      )
    } else {
      for (indirect in source.indirectAcquisitions) {
        paths.addAll(
          indirectPaths(source, indirect).map { path ->
            path.prefixWith(source.type, source.uri, source.properties)
          },
        )
      }
    }
    return paths
  }

  /**
   * Transform the given list of acquisitions into a list of acquisition paths.
   */
  fun linearize(acquisitions: List<FeedAcquisition>): List<FeedAcquisitionPath> {
    val paths = mutableListOf<FeedAcquisitionPath>()
    for (acquisition in acquisitions) {
      paths.addAll(linearize(acquisition))
    }
    return paths
  }

  /**
   * Transform the given acquisition into a list of acquisition paths.
   */
  fun linearize(acquisition: FeedAcquisition): List<FeedAcquisitionPath> = acquisitionPaths(acquisition)
}
