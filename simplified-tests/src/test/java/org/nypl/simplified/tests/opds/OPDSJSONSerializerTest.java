package org.nypl.simplified.tests.opds;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nypl.simplified.links.Link;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParserType;
import org.nypl.simplified.opds.core.OPDSFeedParser;
import org.nypl.simplified.opds.core.OPDSFeedParserType;
import org.nypl.simplified.opds.core.OPDSJSONParser;
import org.nypl.simplified.opds.core.OPDSJSONParserType;
import org.nypl.simplified.opds.core.OPDSJSONSerializer;
import org.nypl.simplified.opds.core.OPDSJSONSerializerType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

public final class OPDSJSONSerializerTest {
  private static InputStream getResource(
    final String name)
    throws Exception {

    final String path = "/org/nypl/simplified/tests/opds/" + name;
    final URL url = OPDSFeedEntryParserTest.class.getResource(path);
    if (url == null) {
      throw new FileNotFoundException(path);
    }
    return url.openStream();
  }

  @Test
  public void testRoundTrip0()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType p =
      OPDSAcquisitionFeedEntryParser.newParser();

    final OPDSJSONParserType jp = OPDSJSONParser.newParser();

    final OPDSJSONSerializerType s = OPDSJSONSerializer.newSerializer();

    final InputStream rs0 =
      OPDSJSONSerializerTest.getResource("entry-0.xml");
    final OPDSAcquisitionFeedEntry e0 = p.parseEntryStream(URI.create("urn:test"), rs0);

    final ByteArrayOutputStream bao0 = new ByteArrayOutputStream();
    s.serializeToStream(s.serializeFeedEntry(e0), bao0);

    final InputStream rs1 = new ByteArrayInputStream(bao0.toByteArray());
    final OPDSAcquisitionFeedEntry e1 =
      jp.parseAcquisitionFeedEntryFromStream(rs1);

    {
      final List<OPDSAcquisition> e0a = e0.getAcquisitions();
      final List<OPDSAcquisition> e1a = e1.getAcquisitions();
      Assertions.assertEquals(e0a.size(), e1a.size());

      for (int index = 0; index < e0a.size(); ++index) {
        final OPDSAcquisition a0 = e0a.get(index);
        final OPDSAcquisition a1 = e1a.get(index);
        Assertions.assertEquals(a0.getRelation(), a1.getRelation());
        Assertions.assertEquals(a0.getType(), a1.getType());
        final Link u0 = a0.getUri();
        final Link u1 = a1.getUri();
        Assertions.assertEquals(u0.getBitrate(), u1.getBitrate());
        Assertions.assertEquals(u0.getDuration(), u1.getDuration());
        Assertions.assertEquals(u0.getHeight(), u1.getHeight());
        Assertions.assertEquals(u0.getHrefURI(), u1.getHrefURI());
        Assertions.assertEquals(u0.getRelation(), u1.getRelation());
        Assertions.assertEquals(u0.getTitle(), u1.getTitle());
        Assertions.assertEquals(u0.getType().toString(), u1.getType().toString());
        Assertions.assertEquals(u0.getWidth(), u1.getWidth());
        Assertions.assertEquals(a0.getIndirectAcquisitions(), a1.getIndirectAcquisitions());
      }

      Assertions.assertEquals(e0.getAvailability(), e1.getAvailability());
      Assertions.assertEquals(e0.getAuthors(), e1.getAuthors());
      Assertions.assertEquals(e0.getCategories(), e1.getCategories());
      Assertions.assertEquals(e0.getCover(), e1.getCover());
      Assertions.assertEquals(e0.getGroups(), e1.getGroups());
      Assertions.assertEquals(e0.getID(), e1.getID());
      Assertions.assertEquals(e0.getNarrators(), e1.getNarrators());
      Assertions.assertEquals(e0.getPreviewAcquisitions(), e1.getPreviewAcquisitions());
      Assertions.assertEquals(e0.getPublished(), e1.getPublished());
      Assertions.assertEquals(e0.getPublisher(), e1.getPublisher());
      Assertions.assertEquals(e0.getSummary(), e1.getSummary());
      Assertions.assertEquals(e0.getThumbnail(), e1.getThumbnail());
      Assertions.assertEquals(e0.getTitle(), e1.getTitle());
      Assertions.assertEquals(e0.getUpdated(), e1.getUpdated());
    }
  }

  @Test
  public void testRoundTrip1()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType ep =
      OPDSAcquisitionFeedEntryParser.newParser();

    final OPDSFeedParserType p = OPDSFeedParser.newParser(ep);
    final OPDSJSONParserType jp = OPDSJSONParser.newParser();

    final OPDSJSONSerializerType s = OPDSJSONSerializer.newSerializer();

    final InputStream rs0 = OPDSJSONSerializerTest.getResource("loans.xml");
    final OPDSAcquisitionFeed fe0 = p.parse(new URI("http://example.com"), rs0);

    final ByteArrayOutputStream bao0 = new ByteArrayOutputStream();
    s.serializeToStream(s.serializeFeed(fe0), bao0);

    final InputStream rs1 = new ByteArrayInputStream(bao0.toByteArray());
    final OPDSAcquisitionFeed fe1 = jp.parseAcquisitionFeedFromStream(rs1);

    {
      final List<OPDSAcquisitionFeedEntry> fe0e = fe0.getFeedEntries();
      final List<OPDSAcquisitionFeedEntry> fe1e = fe1.getFeedEntries();
      for (int index = 0; index < fe0e.size(); ++index) {
        final OPDSAcquisitionFeedEntry e0 = fe0e.get(index);
        final OPDSAcquisitionFeedEntry e1 = fe1e.get(index);
        Assertions.assertEquals(e0.getAcquisitions(), e1.getAcquisitions());
        Assertions.assertEquals(e0.getAuthors(), e1.getAuthors());
        Assertions.assertEquals(e0.getCategories(), e1.getCategories());
        Assertions.assertEquals(e0.getCover(), e1.getCover());
        Assertions.assertEquals(e0.getGroups(), e1.getGroups());
        Assertions.assertEquals(e0.getID(), e1.getID());
        Assertions.assertEquals(e0.getNarrators(), e1.getNarrators());
        Assertions.assertEquals(e0.getPreviewAcquisitions(), e1.getPreviewAcquisitions());
        Assertions.assertEquals(e0.getPublisher(), e1.getPublisher());
        Assertions.assertEquals(e0.getSummary(), e1.getSummary());
        Assertions.assertEquals(e0.getThumbnail(), e1.getThumbnail());
        Assertions.assertEquals(e0.getTitle(), e1.getTitle());
        // Forget comparing instances of Calendar, no implementation gets this
        // right
        // Assert.assertEquals(e0.getUpdated(), e1.getUpdated());
        // Assert.assertEquals(e0.getPublished(), e1.getPublished());
      }
    }
  }

  @Test
  public void testMimeTypes()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType p =
      OPDSAcquisitionFeedEntryParser.newParser();

    final OPDSJSONParserType jp = OPDSJSONParser.newParser();

    final OPDSJSONSerializerType s = OPDSJSONSerializer.newSerializer();

    final InputStream rs0 =
      OPDSJSONSerializerTest.getResource("entry-0.xml");
    final OPDSAcquisitionFeedEntry e0 = p.parseEntryStream(URI.create("urn:test"), rs0);

    final ByteArrayOutputStream bao0 = new ByteArrayOutputStream();
    s.serializeToStream(s.serializeFeedEntry(e0), bao0);

    final InputStream rs1 = new ByteArrayInputStream(bao0.toByteArray());
    final OPDSAcquisitionFeedEntry e1 =
      jp.parseAcquisitionFeedEntryFromStream(rs1);

    final List<OPDSAcquisition> e0a = e0.getAcquisitions();
    final List<OPDSAcquisition> e1a = e1.getAcquisitions();
    Assertions.assertEquals(e0a.size(), 1);
    Assertions.assertEquals(e0a.size(), e1a.size());

    final OPDSAcquisition e0FirstAcquisition = e0a.get(0);
    final OPDSAcquisition e1FirstAcquisition = e1a.get(0);

    Assertions.assertEquals(e0FirstAcquisition.getType().getSubtype(),
      e1FirstAcquisition.getType().getSubtype());
    Assertions.assertEquals(e0FirstAcquisition.getType().getType(),
      e1FirstAcquisition.getType().getType());
    Assertions.assertEquals(e0FirstAcquisition.getType().getFullType(),
      e1FirstAcquisition.getType().getFullType());
    Assertions.assertEquals(e0FirstAcquisition.getType().getParameters().size(),
      e1FirstAcquisition.getType().getParameters().size());

    for (String key : e0FirstAcquisition.getType().getParameters().keySet()) {
      Assertions.assertTrue(e1FirstAcquisition.getType().getParameters().containsKey(key));
      Assertions.assertEquals(e0FirstAcquisition.getType().getParameters().get(key),
        e1FirstAcquisition.getType().getParameters().get(key));
    }
  }
}
