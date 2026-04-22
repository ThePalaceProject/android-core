window.addEventListener("webviewerloaded", () => {
  PDFViewerApplication.initializedPromise.then(() => {
    // Listen for page changes and notify the activity, so that reading position can be saved.
    console.log("PDFViewerApplication is initialized.")

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
});

/**
 * Notify the activity of a click on the PDF viewer.
 **/

function onPDFViewerClick() {
  PDFListener.onPageClick();
}

/**
 * Toggle the sidebar.
 **/

function toggleSidebar() {
  PDFViewerApplication.viewsManager.toggle();
  return PDFViewerApplication.viewsManager.isOpen;
}

/**
 * Toggle the secondary toolbar.
 **/

function toggleSecondaryToolbar() {
  PDFViewerApplication.secondaryToolbar.toggle();
  return PDFViewerApplication.secondaryToolbar.isOpen;
}
