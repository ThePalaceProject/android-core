# Contents

These files implement the Palace PDF viewer, based on pdf.js.

## pdfjs-{version}-dist

The generic build of pdf.js and its viewer. There are a few ways to obtain this:

- For the latest release: Download as a zip archive from
  [mozilla.github.io](https://mozilla.github.io/pdf.js/getting_started/#download).
  Look for the link to the prebuilt package for modern browsers that includes the generic build of
  pdf.js and the viewer.
- For the latest or older releases: Download as a zip archive from
  [GitHub](https://github.com/mozilla/pdf.js/releases). Look for the desired version, and download
  the build for modern browsers (not including `legacy` in the filename).
- For unreleased development code, or any release: Build from source. Clone
  `https://github.com/mozilla/pdf.js`, and follow the instructions in the
  [README](https://github.com/mozilla/pdf.js/blob/master/README.md). Running `gulp generic` will
  create the `build/generic` directory that has the contents of this directory.

Note that the `pdfjs-dist` module available from npm or an npm-sourced CDN (e.g. jsDelivr, cdnjs,
UNPKG) will *not* work, because the package published to npm does not contain the generic viewer
that serves as the basis for the Palace viewer.

To upgrade the version of pdf.js, download and unzip the desired varsion, and copy the directory
into `assets`, as `pdfjs-{version}-dist`. Alternatively, build the desired version from source, and
copy the `build/generic` directory into `assets`, as `pdfjs-{version}-dist`. Update the URLs in
`viewer.html` and `viewer.js` to reference the new directory. Other changes to `viewer.html`,
`viewer.js`, and `viewer.css` may be needed, depending on what has changed in pdf.js from the
previous version.

The distribution of pdf.js includes a sample PDF file, `web/compressed.tracemonkey-pldi-09.pdf`.
This file can be deleted, as there is no need to distribute it with the app.

## viewer.html

The HTML page that displays the PDF viewer, when loaded into a WebView. This is a slightly
modified copy of `viewer.html` from `pdfjs-{version}-dist/web/viewer.html`.

## viewer.js

JavaScript that customizes the behavior of the generic PDF viewer from pdf.js.

## viewer.css

CSS rules that override the default styling from pdf.js.
