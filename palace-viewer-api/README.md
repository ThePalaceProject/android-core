org.librarysimplified.viewer.api
===

The `org.librarysimplified.viewer.api` module provides a generic API
for instantiating _viewers_ for books.

A _viewer_ is a piece of code that can display specific types of books.
For example, _Readium_ is a _viewer_ of EPUB books. The NYPL's AudioBook
API is a _viewer_ of audio books. Viewers must register themselves by
implementing an [SPI](../palace-viewer-spi/README.md) and registering 
the implementation via `ServiceLoader`.

#### See Also

* [AudioBook](https://github.com/NYPL-Simplified/audiobook-android)
* [org.librarysimplified.viewer.audiobook](../palace-viewer-audiobook/README.md)
* [org.librarysimplified.viewer.epub.readium1](../palace-viewer-epub-readium1/README.md)
* [org.librarysimplified.viewer.pdf.pdfjs](../palace-viewer-pdf-pdfjs/README.md)
* [org.librarysimplified.viewer.spi](../palace-viewer-spi/README.md)
* [Readium](https://www.readium.org)
