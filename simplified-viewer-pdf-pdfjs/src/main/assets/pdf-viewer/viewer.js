PDFViewerApplicationOptions.set("workerSrc", "./pdfjs-2.14.305-dist/lib/pdf.worker.js");
PDFViewerApplicationOptions.set("defaultUrl", "/book.pdf");
PDFViewerApplicationOptions.set("defaultZoomValue", "page-width");

PDFViewerApplication.initializedPromise.then(() => {
  // Listen for page changes and notify the activity, so that reading position can be saved.

  PDFViewerApplication.eventBus.on("pagechanging", () => {
    // PDFListener is an object supplied to the WebView by PdfReaderActivity, allowing JavaScript to
    // interface with the Android activity.

    PDFListener.onPageChanged(PDFViewerApplication.page);
  }, { external: true });

  // Detect click events on the PDF viewer.

  const viewers = document.getElementsByClassName("pdfViewer");

  if (viewers.length > 0) {
    viewers[0].addEventListener("click", onPDFViewerClick, { passive: true });
  }
});

/**
 * Notify the activity of a click on the PDF viewer.
 **/
function onPDFViewerClick() {
  PDFListener.onPageClick();
}

/**
 * Toggle the sidebar.
 *
 * @return true if the sidebar is open after toggling, false if it is closed.
 **/
function toggleSidebar() {
  PDFViewerApplication.pdfSidebar.toggle();

  return PDFViewerApplication.pdfSidebar.isOpen;
}

/**
 * Toggle the secondary toolbar.
 *
 * @return true if the secondary toolbar is open after toggling, false if it is closed.
 **/
function toggleSecondaryToolbar() {
  PDFViewerApplication.secondaryToolbar.toggle();

  return PDFViewerApplication.secondaryToolbar.isOpen;
}
