package org.nypl.simplified.tests.feeds.opds20

import java.net.URI
import org.junit.Test
import org.junit.runner.RunWith
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.opds20.OPDS20FeedConverter
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.shared.util.Url
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class OPDS20FeedConverterTest {

  private val accountID: AccountID =
    AccountID(java.util.UUID.randomUUID())

  private fun parse(json: String): ParseData {
    val url: Url = Url("https://example.com/feed.json")!!
    return OPDS2Parser.parse(json.toByteArray(StandardCharsets.UTF_8), url)
  }

  @Test
  fun testEmptyFeed() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": []
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals("Test Feed", feedData.feedTitle)
    assertEquals(URI("https://example.com/feed.json"), feedData.feedURI)
    assertEquals(0, feedData.size)
  }

  @Test
  fun testFeedWithPublication() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:1234567890",
              "title": "Test Book",
              "author": [
                {"name": "Author One"}
              ],
              "published": "2024-01-15T00:00:00Z"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "mediaType": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(1, feedData.size)

    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals("Test Book", entry.title)
    assertEquals("urn:isbn:1234567890", entry.id)
    assertEquals(listOf("Author One"), entry.authors)
    assertEquals(1, entry.acquisitions.size)
  }

  @Test
  fun testFeedWithFacets() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [],
        "facets": [
          {
            "metadata": {
              "title": "Genre"
            },
            "links": [
              {
                "href": "https://example.com/feed.json?genre=fiction",
                "rel": "self"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(1, feedData.facetsOrder.size)

    val facet = feedData.facetsOrder[0] as FeedFacetData
    assertEquals("Genre", facet.title)
    assertTrue(facet.isActive)
  }

  @Test
  fun testFeedWithGroups() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "groups": [
          {
            "metadata": {
              "title": "New Releases"
            },
            "links": [
              {
                "href": "https://example.com/group/new",
                "rel": "self"
              }
            ],
            "publications": [
              {
                "type": "publication",
                "metadata": {
                  "identifier": "urn:isbn:0000000001",
                  "title": "Grouped Book"
                },
                "links": []
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedDataWithGroups)
    val feedData = converted as FeedDataWithGroups
    assertEquals(1, feedData.groups.size)
    assertEquals("New Releases", feedData.groups[0].groupTitle)
    assertEquals(1, feedData.groups[0].groupEntries.size)
  }

  @Test
  fun testFeedMetadata() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed",
          "numberOfItems": 42,
          "itemsPerPage": 10,
          "currentPage": 1,
          "modified": "2024-06-15T12:00:00Z"
        },
        "publications": []
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals("Test Feed", feedData.feedTitle)
    assertNotNull(feedData.feedUpdated)
  }

  @Test
  fun testEntryWithSubjects() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:1111111111",
              "title": "Book With Subjects",
              "subject": [
                {
                  "name": "Fiction",
                  "code": "FICTION",
                  "scheme": "http://example.com/schemes/genre"
                }
              ]
            },
            "links": []
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals(1, entry.categories.size)
    assertEquals("Fiction", entry.categories[0].label)
    assertEquals("FICTION", entry.categories[0].term)
  }

  @Test
  fun testEntryWithMultipleAuthors() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:2222222222",
              "title": "Multi Author Book",
              "author": [
                {"name": "Author One"},
                {"name": "Author Two"},
                {"name": "Author Three"}
              ]
            },
            "links": []
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals(listOf("Author One", "Author Two", "Author Three"), entry.authors)
    assertEquals("Author One, Author Two, Author Three", entry.authorsCommaSeparated)
  }

  @Test
  fun testEntryWithLanguageAndPublisher() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:3333333333",
              "title": "Detailed Book",
              "language": ["en"],
              "publisher": [
                {"name": "Test Publisher"}
              ],
              "description": "A test book description",
              "duration": 3600.0
            },
            "links": []
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals("en", entry.language)
    assertEquals("Test Publisher", entry.publisher)
    assertEquals("A test book description", entry.summary)
    assertEquals(3600.0, entry.duration!!, 0.001)
  }

  @Test
  fun testFeedNextLink() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [],
        "links": [
          {
            "href": "https://example.com/feed.json?page=2",
            "rel": "next"
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/feed.json?page=2"), feedData.feedNext)
  }

  @Test
  fun testOrderPreserved() {
    val json = """
      {
        "type": "feed",
        "title": "Test Feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:0000000001",
              "title": "First Book"
            },
            "links": []
          },
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:0000000002",
              "title": "Second Book"
            },
            "links": []
          },
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:0000000003",
              "title": "Third Book"
            },
            "links": []
          }
        ]
      }
    """.trimIndent()

    val parseData = parse(json)
    val feed = parseData.feed!!
    val converted = OPDS20FeedConverter.convert(accountID, feed)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(3, feedData.size)
    assertEquals("First Book", (feedData.entries[0] as FeedEntryBook).title)
    assertEquals("Second Book", (feedData.entries[1] as FeedEntryBook).title)
    assertEquals("Third Book", (feedData.entries[2] as FeedEntryBook).title)
  }
}
