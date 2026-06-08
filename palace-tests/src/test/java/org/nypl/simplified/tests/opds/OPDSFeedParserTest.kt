package org.nypl.simplified.tests.opds

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser.Companion.newParser
import org.nypl.simplified.opds.core.OPDSFeedParser.Companion.newParser
import org.nypl.simplified.opds.core.OPDSParseException
import org.nypl.simplified.opds.core.OPDSSearchLink
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.DOMException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

class OPDSFeedParserTest {
  private val logger: Logger = LoggerFactory.getLogger(OPDSFeedParserTest::class.java)

  @Test
  @Throws(Exception::class)
  fun testAcquisitionFeedFiction0() {
    val uri = URI.create(
      "http://circulation.alpha.librarysimplified.org/feed/Picture%20Books"
    )
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("acquisition-fiction-0.xml")
    val f = p.parse(uri, d)
    d.close()

    assertEquals(
      "https://d5v0j5lesri7q.cloudfront.net/NYBKLYN/groups/",
      f.feedID
    )
    assertEquals("All Books", f.feedTitle)
    Assertions.assertEquals(0, f.feedEntries.size)
    Assertions.assertEquals(9, f.feedGroups.size)

    val search_opt =
      f.feedSearchURI
    val search = search_opt!!
    assertEquals(
      URI.create("https://bplsimplye.bklynlibrary.org/NYBKLYN/search/"),
      search.uri
    )
    assertEquals(
      "application/opensearchdescription+xml",
      search.type
    )

    val u = f.feedUpdated
    val ids: MutableSet<String?> = HashSet()
    val titles: MutableSet<String?> = HashSet()

    for (e in f.feedEntries) {
      val e_id = e.id
      val e_title = e.title
      val e_u = e.updated
      val e_acq = e.acquisitions
      val e_authors = e.authors
      val e_narrators = e.narrators
      val e_thumb = e.thumbnail
      val e_cover = e.cover

      print("authors: ")
      for (a in e_authors) {
        print(a)
      }
      println()

      print("acquisitions: ")
      for (ea in e_acq) {
        print(ea)
      }

      print("narrators: ")
      for (n in e_narrators) {
        print(n)
      }
      println()

      println()
      println("id: " + e_id)
      println("title: " + e_title)
      println("update: " + e_u)
      println("thumbnail: " + e_thumb)
      println("cover: " + e_cover)

      Assertions.assertTrue(e.publisher != null)
      Assertions.assertTrue(e_authors.size > 0)
      Assertions.assertTrue(e_narrators.size > 0)
      Assertions.assertTrue(e_acq.size > 0)

      if (ids.contains(e_id)) {
        throw AssertionError("Duplicate ID: " + e_id)
      }
      ids.add(e_id)

      println("--")
    }
  }

  @Test
  @Throws(Exception::class)
  fun testAcquisitionFeedGroups0() {
    val uri =
      URI.create("http://circulation.alpha.librarysimplified.org/groups/")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("acquisition-groups-0.xml")
    val f = p.parse(uri, d)
    d.close()

    val entries = f.feedEntries
    for (index in entries.indices) {
      println(entries.get(index).title)
    }

    val groups = f.feedGroups
    Assertions.assertTrue(entries.isEmpty())
    Assertions.assertEquals(24, groups.keys.size)

    for (name in groups.keys) {
      println(name)
      val group = groups.get(name)!!
      assertEquals(group.title, name)
      Assertions.assertTrue(!group.entries.isEmpty())
    }
  }

  @Test
  @Throws(Exception::class)
  fun testAcquisitionFeedPaginated0() {
    val uri = URI.create(
      "http://library-simplified.herokuapp"
        + ".com/feed/Biography%20%26%20Memoir?order=author"
    )
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("acquisition-paginated-0.xml")
    val f = p.parse(uri, d)
    d.close()

    assertEquals("/NYNYPL/feed/13", f.feedID)
    assertEquals("Historical Fiction", f.feedTitle)
    Assertions.assertEquals(50, f.feedEntries.size)

    val next_opt = f.feedNext

    assertEquals(
      "https://d2txvnljjb5oij.cloudfront.net/NYNYPL/feed/13?available=now&collection=full&entrypoint=Book&key=%5B%22%5Cu1516%5Cu1693%5Cu49c7%5Cu2443%5Cu5152%5Cu1011%5Cu1098%5Cu4646%5Cu1a03%5Cu0114%5Cu0706%5Cu2011%5Cu480e%5Cu72f3%5Cu3981%5Cu5c06%5Cu0000%5Cu0001%22%2C+%22%5Cu2b1c%5Cu0e94%5Cu0640%5Cu6043%5Cu6192%5Cu1111%5Cu3402%5Cu1001%5Cu7060%5Cu3702%5Cu0000%5Cu0001%22%2C+264205%5D&order=title&size=50",
      next_opt.toString()
    )
  }

  @Test
  @Throws(Exception::class)
  fun testDOMException() {
    val uri =
      URI.create("http://library-simplified.herokuapp.com/feed/Fiction")

    val ep =
      newParser()
    val p = newParser(ep)
    val d: InputStream =
      object : InputStream() {
        @Throws(IOException::class)
        override fun read(): Int {
          throw DOMException(0.toShort(), "Bad news")
        }
      }

    Assertions.assertThrows(OPDSParseException::class.java, Executable {
      p.parse(uri, d)
    })
  }

  @Test
  @Throws(Exception::class)
  fun testEmpty0() {
    val uri =
      URI.create("http://library-simplified.herokuapp.com/feed/Fiction")
    val p =
      newParser(newParser())
    val d: InputStream = getResource("empty-0.xml")
    val f = p.parse(uri, d)
    f!!
    d.close()
  }

  @Test
  @Throws(Exception::class)
  fun testEntryAsFeed0() {
    val uri =
      URI.create("http://library-simplified.herokuapp.com/feed/Fiction")
    val p =
      newParser(newParser())
    val d: InputStream = getResource("entry-0.xml")
    val f = p.parse(uri, d)!!

    Assertions.assertEquals(f.feedEntries.size, 1)

    d.close()
  }

  @Test
  @Throws(Exception::class)
  fun testNotXMLException() {
    val uri =
      URI.create("http://library-simplified.herokuapp.com/feed/Fiction")

    val p = newParser(
      newParser()
    )
    val d: InputStream =
      getResource("bad-not-xml.xml")

    Assertions.assertThrows(OPDSParseException::class.java, Executable {
      p.parse(uri, d)
    })
  }

  @Test
  @Throws(Exception::class)
  fun testParserURISyntaxException() {
    val uri =
      URI.create("http://library-simplified.herokuapp.com/feed/Fiction")

    val p = newParser(
      newParser()
    )
    val d: InputStream =
      getResource("bad-uri-syntax.xml")
    val result =
      p.parse(uri, d)
    val errors =
      result.errors

    Assertions.assertEquals(1, errors.size)
    assertEquals(
      URISyntaxException::class.java,
      errors.get(0).exception!!.javaClass
    )
  }

  @Test
  @Throws(Exception::class)
  fun testStreamIOException() {
    val uri =
      URI.create("http://library-simplified.herokuapp.com/feed/Fiction")

    val p = newParser(
      newParser()
    )
    val d: InputStream = object : InputStream() {
      @Throws(IOException::class)
      override fun read(): Int {
        throw IOException()
      }
    }

    Assertions.assertThrows(OPDSParseException::class.java, Executable {
      p.parse(uri, d)
    })
  }

  @Test
  @Throws(Exception::class)
  fun testAcquisitionFeedCategories0() {
    val uri = URI.create(
      "http://circulation.alpha.librarysimplified.org/feed/Picture%20Books"
    )
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("acquisition-categories-0.xml")
    val f = p.parse(uri, d)
    d.close()

    val e = f.feedEntries.get(0)
    val ec = e.categories

    Assertions.assertEquals(3, ec.size)

    val ec0 = ec.get(0)
    assertEquals(ec0.term, "Children")
    assertEquals(ec0.scheme, "http://schema.org/audience")

    val ec1 = ec.get(1)
    assertEquals(ec1.term, "3")
    assertEquals(
      ec1.scheme, "http://schema.org/typicalAgeRange"
    )

    val ec2 = ec.get(2)
    assertEquals(ec2.term, "Nonfiction")
    assertEquals(
      ec2.scheme, "http://librarysimplified.org/terms/genres/Simplified/"
    )
  }

  @Test
  @Throws(Exception::class)
  fun testAcquisitionFeedFacets0() {
    val uri = URI.create(
      "http://circulation.alpha.librarysimplified.org/feed/Picture%20Books"
    )
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("acquisition-facets-0.xml")
    val f = p.parse(uri, d)
    d.close()

    val fbg = f.feedFacetsByGroup
    val fo = f.feedFacetsOrder

    Assertions.assertEquals(2, fo.size)
    Assertions.assertEquals(1, fbg.size)
    Assertions.assertTrue(fbg.containsKey("Sort by"))

    val sorted = fbg.get("Sort by")
    Assertions.assertEquals(2, sorted!!.size)

    run {
      val fi = sorted.get(0)
      assertEquals("Sort by", fi.group)
      assertEquals("Title", fi.title)
      Assertions.assertFalse(fi.isActive)
      assertEquals(
        URI.create(
          "http://circulation.alpha.librarysimplified"
            + ".org/feed/Picture%20Books?order=title"
        ), fi.uri
      )
    }

    run {
      val fi = sorted.get(1)
      assertEquals("Sort by", fi.group)
      assertEquals("Author", fi.title)
      Assertions.assertTrue(fi.isActive)
      assertEquals(
        URI.create(
          "http://circulation.alpha.librarysimplified"
            + ".org/feed/Picture%20Books?order=author"
        ), fi.uri
      )
    }
  }

  @Test
  @Throws(Exception::class)
  fun testAcquisitionFeedFacets1() {
    val uri = URI.create(
      "http://circulation.alpha.librarysimplified.org/feed/Picture%20Books"
    )
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("acquisition-facets-1.xml")
    val f = p.parse(uri, d)
    d.close()

    val fbg = f.feedFacetsByGroup
    val fo = f.feedFacetsOrder

    Assertions.assertEquals(2, fo.size)
    Assertions.assertEquals(1, fbg.size)
    Assertions.assertTrue(fbg.containsKey("Sort by"))

    val sorted = fbg.get("Sort by")
    Assertions.assertEquals(2, sorted!!.size)

    run {
      val fi = sorted.get(0)
      assertEquals("Sort by", fi.group)
      assertEquals("Something", fi.groupType)
      assertEquals("Title", fi.title)
      Assertions.assertFalse(fi.isActive)
      assertEquals(
        URI.create(
          "http://circulation.alpha.librarysimplified"
            + ".org/feed/Picture%20Books?order=title"
        ), fi.uri
      )
    }

    run {
      val fi = sorted.get(1)
      assertEquals("Sort by", fi.group)
      assertEquals("Something", fi.groupType)
      assertEquals("Author", fi.title)
      Assertions.assertTrue(fi.isActive)
      assertEquals(
        URI.create(
          "http://circulation.alpha.librarysimplified"
            + ".org/feed/Picture%20Books?order=author"
        ), fi.uri
      )
    }
  }

  @Test
  @Throws(Exception::class)
  fun testAnalytics20190509() {
    val uri = URI.create("urn:example")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("analytics-20190509.xml")
    val f = p.parse(uri, d)
    d.close()

    for (e in f.feedEntries) {
      val analytics = e.analytics!!
      this.logger.debug("analytics: {}", e.analytics)
      Assertions.assertTrue(
        analytics.toString().contains("open_book"),
        "URI must contain 'open_book'"
      )
    }
  }

  @Test
  @Throws(Exception::class)
  fun testFeedBooks20190509() {
    val uri = URI.create("http://www.example.com/")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("feedbooks-20190808.xml")
    val f = p.parse(uri, d)
    d.close()

    assertEquals(
      URI.create("http://www.example.com/opensearch.xml"),
      f.feedSearchURI?.let(OPDSSearchLink::uri)
    )
  }

  @Test
  @Throws(Exception::class)
  fun testDPLATestFeed() {
    val uri = URI.create("http://www.example.com/")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("dpla-test-feed.xml")
    val f = p.parse(uri, d)

    val groupEntry =
      f.feedGroups.entries.iterator().next()
    val group =
      groupEntry.value
    val entry =
      group.entries.get(0)
    val acquisitions =
      entry.acquisitions
    val acquisition =
      acquisitions.get(0)

    Assertions.assertEquals(1, acquisition.availableFinalContentTypes().size)
    val finalType = acquisition.availableFinalContentTypes().iterator().next()
    assertEquals("application", finalType.type)
    assertEquals("audiobook+json", finalType.subtype)
    assertEquals(
      "http://www.feedbooks.com/audiobooks/access-restriction",
      finalType.parameters.get("profile")
    )

    Assertions.assertEquals(1, acquisitions.size)
    d.close()
  }

  @Test
  @Throws(Exception::class)
  fun testMinotaur20231113Durations() {
    val uri = URI.create("http://www.example.com/")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("minotaur-20231113.xml")

    val f = p.parse(uri, d)

    val group = f.feedGroups.get("Palace Marketplace")!!
    val entry =
      group.entries
        .stream()
        .filter({ e -> e.id == "urn:isbn:9781442370289" })
        .findFirst()
        .orElseThrow()

    assertEquals(36914.0, entry.duration)
    d.close()
  }

  @Test
  @Throws(Exception::class)
  fun testBoundless20250724Entries() {
    val uri = URI.create("http://www.example.com/")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("search-boundless-20250724.xml")

    val f = p.parse(uri, d)
    val entries = f.feedEntries

    run {
      val entry = entries.get(0)
      assertEquals("Myp Chemistry", entry.title)
    }

    Assertions.assertEquals(10, entries.size)
    d.close()
  }

  @Test
  @Throws(Exception::class)
  fun testSeries() {
    val uri = URI.create("http://www.example.com/")
    val p =
      newParser(newParser())
    val d: InputStream =
      getResource("a1qa-20260605.xml")

    val f = p.parse(uri, d)
    val entries = f.feedGroups[f.feedGroupsOrder[12]]!!.entries

    val series = entries[0].series!!
    assertEquals(1, series.position)
    assertEquals("https://gorgon.staging.palaceproject.io/a1qa-test/works/series/Maze%20Runner/eng/All+Ages,Children,Young+Adult", series.link.toString())

    Assertions.assertEquals(15, entries.size)
    d.close()
  }

  companion object {
    @Throws(Exception::class)
    private fun getResource(
      name: String
    ): InputStream {
      val path = "/org/nypl/simplified/tests/opds/" + name
      val url = OPDSFeedEntryParserTest::class.java.getResource(path)
      if (url == null) {
        throw FileNotFoundException(path)
      }
      return url.openStream()
    }
  }
}
