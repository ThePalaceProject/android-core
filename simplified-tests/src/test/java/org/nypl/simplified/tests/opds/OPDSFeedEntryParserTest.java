package org.nypl.simplified.tests.opds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nypl.simplified.opds.core.DRMLicensor;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParserType;
import org.nypl.simplified.opds.core.OPDSAvailabilityHeld;
import org.nypl.simplified.opds.core.OPDSAvailabilityHeldReady;
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable;
import org.nypl.simplified.opds.core.OPDSAvailabilityLoanable;
import org.nypl.simplified.opds.core.OPDSAvailabilityLoaned;
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess;
import org.nypl.simplified.opds.core.OPDSAvailabilityType;
import org.nypl.simplified.opds.core.OPDSDateParsers;
import org.nypl.simplified.opds.core.OPDSIndirectAcquisition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

import one.irradia.mime.api.MIMEType;
import one.irradia.mime.vanilla.MIMEParser;

/**
 * Entry parser contract.
 */

public final class OPDSFeedEntryParserTest {

  private static final Logger LOG =
    LoggerFactory.getLogger(OPDSFeedEntryParserTest.class);

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
  public void testEntryAvailabilityLoanable()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-loanable.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();
    final OPDSAvailabilityLoanable expected = OPDSAvailabilityLoanable.get();
    assertEquals(expected, availability);

    assertEquals(1, e.getAcquisitions().size());
  }

  @Test
  public void testEntryAvailabilityLoanedIndefinite()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-loaned-indefinite.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_start_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2000-01-01T00:00:00Z"));
    final OptionType<DateTime> expected_end_date = Option.none();
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityLoaned expected = OPDSAvailabilityLoaned.get(
      expected_start_date, expected_end_date, expected_revoke);

    assertEquals(expected, availability);

    assertEquals(1, e.getAcquisitions().size());
    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityLoanedTimed()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-loaned-timed.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_start_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2000-01-01T00:00:00Z"));
    final OptionType<DateTime> expected_end_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2010-01-01T00:00:00Z"));
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityLoaned expected = OPDSAvailabilityLoaned.get(
      expected_start_date, expected_end_date, expected_revoke);

    assertEquals(expected, availability);

    assertEquals(1, e.getNarrators().size());
    assertEquals(1, e.getAcquisitions().size());
    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityHoldable()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-holdable.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();
    final OPDSAvailabilityHoldable expected = OPDSAvailabilityHoldable.get();

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityHeldIndefinite()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-held-indefinite.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_start_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2000-01-01T00:00:00Z"));
    final OptionType<Integer> queue_position = Option.none();
    final OptionType<DateTime> expected_end_date = Option.none();
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeld expected = OPDSAvailabilityHeld.get(
      expected_start_date, queue_position, expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityHeldTimed()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-held-timed.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_start_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2000-01-01T00:00:00Z"));
    final OptionType<DateTime> expected_end_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2010-01-01T00:00:00Z"));
    final OptionType<Integer> queue_position = Option.none();
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeld expected = OPDSAvailabilityHeld.get(
      expected_start_date, queue_position, expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityHeldIndefiniteQueued()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-held-indefinite-queued.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_start_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2000-01-01T00:00:00Z"));
    final OptionType<Integer> queue_position = Option.some(3);
    final OptionType<DateTime> expected_end_date = Option.none();
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeld expected = OPDSAvailabilityHeld.get(
      expected_start_date, queue_position, expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityHeldTimedQueued()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-held-timed-queued.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_start_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2000-01-01T00:00:00Z"));
    final OptionType<DateTime> expected_end_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2010-01-01T00:00:00Z"));
    final OptionType<Integer> queue_position = Option.some(3);
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeld expected = OPDSAvailabilityHeld.get(
      expected_start_date, queue_position, expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityHeldReady()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-heldready.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_end_date = Option.none();
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeldReady expected =
      OPDSAvailabilityHeldReady.get(expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityReservedTimed()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-heldready-timed.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_end_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2010-01-01T00:00:00Z"));
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeldReady expected =
      OPDSAvailabilityHeldReady.get(expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryAvailabilityOpenAccess()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-open-access.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityOpenAccess expected =
      OPDSAvailabilityOpenAccess.get(expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    assertEquals(0, acquisition.getIndirectAcquisitions().size());
    assertEquals(mimeOf("application/epub+zip"), acquisition.getType());
  }

  @Test
  public void testEntryAvailabilityReservedSpecific0()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "entry-availability-heldready-specific0.xml"));

    final OPDSAvailabilityType availability = e.getAvailability();

    final OptionType<DateTime> expected_end_date = Option.some(
      OPDSDateParsers.dateTimeParser().parseDateTime("2015-08-24T00:30:24Z"));
    final OptionType<URI> expected_revoke =
      Option.some(new URI("http://example.com/revoke"));
    final OPDSAvailabilityHeldReady expected =
      OPDSAvailabilityHeldReady.get(expected_end_date, expected_revoke);

    assertEquals(expected, availability);
    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryMultipleFormats0()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource("entry-with-formats-0.xml"));

    assertEquals(3, e.getAcquisitions().size());

    {
      final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
      Assertions.assertTrue(
        OPDSIndirectAcquisition.Companion.findTypeInOptional(
          mimeOf("application/epub+zip"),
          acquisition.getIndirectAcquisitions()).isSome(),
        "application/epub+zip is available");
    }

    {
      final OPDSAcquisition acquisition = e.getAcquisitions().get(1);
      Assertions.assertTrue(
        OPDSIndirectAcquisition.Companion.findTypeInOptional(
          mimeOf("application/pdf"),
          acquisition.getIndirectAcquisitions()).isSome(),
        "application/pdf is available");
    }

    {
      final OPDSAcquisition acquisition = e.getAcquisitions().get(2);
      Assertions.assertTrue(
        OPDSIndirectAcquisition.Companion.findTypeInOptional(
          mimeOf("text/html"),
          acquisition.getIndirectAcquisitions()).isSome(),
        "text/html is available");
    }
  }

  @Test
  public void testEntryMultipleFormats1()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource("entry-with-formats-1.xml"));

    assertEquals(1, e.getAcquisitions().size());

    final OPDSAcquisition acquisition = e.getAcquisitions().get(0);
    Assertions.assertTrue(
      OPDSIndirectAcquisition.Companion.findTypeInOptional(
        mimeOf("application/epub+zip"),
        acquisition.getIndirectAcquisitions()).isSome(),
      "application/epub+zip is available");
  }

  @Test
  public void testEntryWithDRM()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource("entry-with-drm.xml"));

    OptionType<DRMLicensor> licensor_opt = e.getLicensor();
    Assertions.assertTrue(licensor_opt.isSome());

    DRMLicensor licensor = ((Some<DRMLicensor>) licensor_opt).get();
    assertEquals(
      "NYPL",
      licensor.getVendor());
    assertEquals(
      "NYNYPL|0000000000|XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX|XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      licensor.getClientToken());
    assertEquals(
      Option.some("http://qa.circulation.librarysimplified.org/NYNYPL/AdobeAuth/devices"),
      licensor.getDeviceManager());
  }

  @Test
  public void testEntryDateBug()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    parser.parseEntryStream(
      URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource("date-bug.xml")
    );
  }

  @Test
  public void testBugPP465()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource(
        "bug-pp-465.xml"));

    assertEquals(1, e.getAcquisitions().size());
    assertEquals(List.of(), e.getAuthors());
  }

  private OPDSAcquisitionFeedEntryParserType getParser() {
    return OPDSAcquisitionFeedEntryParser.newParser();
  }

  private MIMEType mimeOf(String text) {
    try {
      return MIMEParser.Companion.parseRaisingException(text);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void testEntryBoundless1()
    throws Exception {
    final OPDSAcquisitionFeedEntryParserType parser = this.getParser();
    final OPDSAcquisitionFeedEntry e = parser.parseEntryStream(URI.create("urn:test"),
      OPDSFeedEntryParserTest.getResource("boundless-1.xml"));

    var a = e.getAcquisitions();
    assertEquals(1, a.size());
    assertEquals(
      "https://minotaur.dev.palaceproject.io/minotaur-test-library/works/796/fulfill/30{?modulus,exponent,device_id}",
      a.get(0).getUri().toTemplated().getHref()
    );
  }
}
