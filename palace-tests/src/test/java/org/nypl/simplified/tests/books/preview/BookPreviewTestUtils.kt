package org.nypl.simplified.tests.books.preview

import okhttp3.mockwebserver.MockWebServer
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser
import java.net.URI

object BookPreviewTestUtils {

  fun opdsFeedEntryOf(text: String): OPDSAcquisitionFeedEntry {
    return OPDSAcquisitionFeedEntryParser.newParser()
      .parseEntryStream(URI.create("urn:stdin"), text.byteInputStream())
  }

  fun opdsFeedEntryOfType(
    webServer: MockWebServer,
    mime: String
  ): OPDSAcquisitionFeedEntry {
    return opdsFeedEntryOf(
      """
    <entry xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
      <title>Example</title>
      <updated>2020-09-17T16:48:51+0000</updated>
      <id>7264f7f8-7bea-4ce6-906e-615406ca38cb</id>
      <link href="${webServer.url("/next")}" rel="preview" type="$mime" />
      <link href="${webServer.url("/next")}" rel="http://opds-spec.org/acquisition/open-access" type="$mime">
        <opds:availability since="2020-09-17T16:48:51+0000" status="available" until="2020-09-17T16:48:51+0000" />
        <opds:holds total="0" />
        <opds:copies available="5" total="5" />
      </link>
    </entry>
    """
    )
  }

  fun opdsFeedEntryOfTypes(
    webServer: MockWebServer,
    mime1: String,
    mime2: String
  ): OPDSAcquisitionFeedEntry {
    return opdsFeedEntryOf(
      """
    <entry xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
      <title>Example</title>
      <updated>2020-09-17T16:48:51+0000</updated>
      <id>7264f7f8-7bea-4ce6-906e-615406ca38cb</id>
      <link href="${webServer.url("/next")}" rel="preview" type="$mime1" />
      <link href="${webServer.url("/next")}" rel="preview" type="$mime2" />
      <link href="${webServer.url("/next")}" rel="http://opds-spec.org/acquisition/open-access" type="$mime1">
        <opds:availability since="2020-09-17T16:48:51+0000" status="available" until="2020-09-17T16:48:51+0000" />
        <opds:holds total="0" />
        <opds:copies available="5" total="5" />
      </link>
    </entry>
    """
    )
  }

  fun opdsFeedEntryNoAcquisitionsOfType(
    webServer: MockWebServer,
    mime: String
  ): OPDSAcquisitionFeedEntry {
    return opdsFeedEntryOf(
      """
    <entry xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
      <title>Example</title>
      <updated>2020-09-17T16:48:51+0000</updated>
      <id>7264f7f8-7bea-4ce6-906e-615406ca38cb</id>
      <link href="${webServer.url("/next")}" rel="http://opds-spec.org/acquisition/open-access" type="$mime">
        <opds:availability since="2020-09-17T16:48:51+0000" status="available" until="2020-09-17T16:48:51+0000" />
        <opds:holds total="0" />
        <opds:copies available="5" total="5" />
      </link>
    </entry>
    """
    )
  }
}
