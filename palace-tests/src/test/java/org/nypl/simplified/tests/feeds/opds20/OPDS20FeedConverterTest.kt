package org.nypl.simplified.tests.feeds.opds20

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.feeds.api.FeedAvailabilityLoanable
import org.nypl.simplified.feeds.api.FeedAvailabilityOpenAccess
import org.nypl.simplified.feeds.api.FeedData
import org.nypl.simplified.feeds.api.FeedDataWithGroups
import org.nypl.simplified.feeds.api.FeedEntryBook
import org.nypl.simplified.feeds.api.FeedFacetData
import org.nypl.simplified.feeds.opds20.OPDS20FeedConverter

/**
 * Tests for the OPDS 2.0 feed converter using the Palace WebPub parser.
 *
 * These tests run on the plain JVM — no Robolectric is required because
 * the WebPub parser is a pure Jackson-based JSON deserializer.
 */
class OPDS20FeedConverterTest {

  private val accountID: AccountID =
    AccountID(java.util.UUID.randomUUID())

  private val feedURI: URI =
    URI("https://example.com/feed.json")

  /**
   * Parse the given JSON string and convert to a PalaceFeed.
   */
  private fun parseAndConvert(json: String) =
    OPDS20FeedConverter.convert(
      accountID = this.accountID,
      feed = OPDS20FeedConverter.parse(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8))),
      feedURI = this.feedURI
    )

  @Test
  fun testEmptyFeed() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": []
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals("Test Feed", feedData.feedTitle)
    assertEquals(this.feedURI, feedData.feedURI)
    assertEquals(0, feedData.size)
  }

  @Test
  fun testFeedWithPublication() {
    val json = """
      {
        "type": "feed",
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
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

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

    val converted = this.parseAndConvert(json)

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
        "metadata": {
          "title": "Test Feed"
        },
        "groups": [
          {
            "metadata": {
              "title": "New Releases",
              "identifier": "urn:group:new"
            },
            "links": [
              {
                "href": "https://example.com/group/new",
                "rel": "self",
                "type": "application/opds+json"
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

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedDataWithGroups)
    val feedData = converted as FeedDataWithGroups
    assertEquals(1, feedData.groups.size)
    assertEquals("New Releases", feedData.groups[0].groupTitle)
    assertEquals(1, feedData.groups[0].groupEntries.size)
    val groupEntry = feedData.groups[0].groupEntries[0] as FeedEntryBook
    assertEquals("Grouped Book", groupEntry.title)
  }

  @Test
  fun testFeedMetadata() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed",
          "modified": "2024-06-15T12:00:00Z"
        },
        "publications": []
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

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
                "Fiction"
              ]
            },
            "links": []
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook
    assertEquals(1, entry.categories.size)
    assertEquals("Fiction", entry.categories[0].label)
  }

  @Test
  fun testEntryWithMultipleAuthors() {
    val json = """
      {
        "type": "feed",
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

    val converted = this.parseAndConvert(json)

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
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:3333333333",
              "title": "Detailed Book",
              "languages": ["en"],
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

    val converted = this.parseAndConvert(json)

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

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/feed.json?page=2"), feedData.feedNext)
  }

  @Test
  fun testOrderPreserved() {
    val json = """
      {
        "type": "feed",
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

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(3, feedData.size)
    assertEquals("First Book", (feedData.entries[0] as FeedEntryBook).title)
    assertEquals("Second Book", (feedData.entries[1] as FeedEntryBook).title)
    assertEquals("Third Book", (feedData.entries[2] as FeedEntryBook).title)
  }

  @Test
  fun testFeedSearch() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [],
        "links": [
          {
            "href": "https://example.com/search?query={searchTerms}",
            "rel": "search",
            "templated": true
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertNotNull(feedData.feedSearch)
  }

  @Test
  fun testFeedTermsOfServiceAndPrivacyPolicyAndAbout() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [],
        "links": [
          {
            "href": "https://example.com/tos",
            "rel": "terms-of-service"
          },
          {
            "href": "https://example.com/privacy",
            "rel": "privacy-policy"
          },
          {
            "href": "https://example.com/about",
            "rel": "about"
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/tos"), feedData.feedTermsOfService)
    assertEquals(URI("https://example.com/privacy"), feedData.feedPrivacyPolicy)
    assertEquals(URI("https://example.com/about"), feedData.feedAbout)
  }

  @Test
  fun testEntryPreviewAcquisitions() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:4444444444",
              "title": "Book With Preview"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow"
              },
              {
                "href": "https://example.com/book-sample.pdf",
                "type": "application/pdf",
                "rel": "http://librarysimplified.org/terms/rel/acquire/sample"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(1, entry.acquisitions.size)
    assertEquals(1, entry.previewAcquisitions.size)
    assertEquals(URI("https://example.com/book-sample.pdf"), entry.previewAcquisitions[0].uri)
  }

  @Test
  fun testEntryAnnotationsAndIssuesAndRelated() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:5555555555",
              "title": "Book With Links"
            },
            "links": [
              {
                "href": "https://example.com/annotations",
                "rel": "http://www.w3.org/ns/oa#annotationService"
              },
              {
                "href": "https://example.com/issues",
                "rel": "issues"
              },
              {
                "href": "https://example.com/related",
                "rel": "related"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(URI("https://example.com/annotations"), entry.annotations)
    assertEquals(URI("https://example.com/issues"), entry.issues)
    assertEquals(URI("https://example.com/related"), entry.related)
  }

  @Test
  fun testEntryAlternateAndAnalyticsAndTimeTracking() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:6666666666",
              "title": "Book With More Links"
            },
            "links": [
              {
                "href": "https://example.com/alternate",
                "rel": "alternate"
              },
              {
                "href": "https://example.com/analytics",
                "rel": "http://librarysimplified.org/terms/rel/analytics/open-book"
              },
              {
                "href": "https://example.com/time-tracking",
                "rel": "http://palaceproject.io/terms/timeTracking"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(URI("https://example.com/alternate"), entry.alternate)
    assertEquals(URI("https://example.com/analytics"), entry.analytics)
    assertEquals(URI("https://example.com/time-tracking"), entry.timeTrackingUri)
  }

  @Test
  fun testEntryGroups() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:8888888888",
              "title": "Book In Groups"
            },
            "links": [
              {
                "href": "https://example.com/group/scifi",
                "rel": "collection",
                "title": "Sci-Fi"
              },
              {
                "href": "https://example.com/group/favorites",
                "rel": "collection",
                "title": "Favorites"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(2, entry.groups.size)
    val groupNames = entry.groups.map { it.name }.toSet()
    assertEquals(setOf("Sci-Fi", "Favorites"), groupNames)
  }

  @Test
  fun testEntryCoverAndThumbnail() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:9999999999",
              "title": "Book With Images"
            },
            "links": [
              {
                "href": "https://example.com/cover.jpg",
                "type": "image/jpeg",
                "rel": "cover"
              },
              {
                "href": "https://example.com/thumb.jpg",
                "type": "image/jpeg",
                "rel": "thumbnail"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(URI("https://example.com/cover.jpg"), entry.cover)
    assertEquals(URI("https://example.com/thumb.jpg"), entry.thumbnail)
  }

  @Test
  fun testEntryNarratorsAndSeries() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:1010101010",
              "title": "Audiobook",
              "narrator": [
                {"name": "Narrator One"}
              ],
              "belongsTo": {
                "series": {
                  "name": "Dune",
                  "position": 1.0,
                  "links": [
                    {"href": "https://example.com/series/dune", "rel": "self"}
                  ]
                }
              }
            },
            "links": []
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(listOf("Narrator One"), entry.narrators)
    assertNotNull(entry.series)
    assertEquals("Dune", entry.series!!.name)
    assertEquals(1, entry.series!!.position)
  }

  @Test
  fun testEntryAvailabilityFromProperties() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:2020202020",
              "title": "Available Book"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow",
                "properties": {
                  "availability": {
                    "state": "available"
                  }
                }
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityLoanable)
  }

  @Test
  fun testEntryOpenAccessAvailability() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:3030303030",
              "title": "Open Access Book"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/open-access"
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is FeedAvailabilityOpenAccess)
  }

  @Test
  fun testFeedLicenses() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [],
        "links": [
          {
            "href": "https://example.com/licenses",
            "rel": "licenses"
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    assertEquals(URI("https://example.com/licenses"), feedData.feedLicenses)
  }

  @Test
  fun testEntryIndirectAcquisitions() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:4040404040",
              "title": "Book With DRM"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow",
                "properties": {
                  "availability": {
                    "state": "available"
                  },
                  "indirectAcquisition": [
                    {
                      "type": "application/vnd.adobe.adept+xml",
                      "child": [
                        {
                          "type": "application/vnd.readium.lcp.licenser+json",
                          "child": []
                        }
                      ]
                    }
                  ]
                }
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertEquals(1, entry.acquisitions.size)
    assertEquals(1, entry.acquisitions[0].indirectAcquisitions.size)
    assertEquals(
      "application/vnd.adobe.adept+xml",
      entry.acquisitions[0].indirectAcquisitions[0].type.fullType
    )
    assertEquals(1, entry.acquisitions[0].indirectAcquisitions[0].indirectAcquisitions.size)
    assertEquals(
      "application/vnd.readium.lcp.licenser+json",
      entry.acquisitions[0].indirectAcquisitions[0].indirectAcquisitions[0].type.fullType
    )
  }

  @Test
  fun testEntryDRMLicensor() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:5050505050",
              "title": "Book With DRM Licensor"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow",
                "properties": {
                  "licensor": {
                    "vendor": "adobe",
                    "clientToken": "test-token",
                    "deviceManager": "https://example.com/devices"
                  }
                }
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertNotNull(entry.licensor)
    assertEquals("adobe", entry.licensor!!.vendor)
    assertEquals("test-token", entry.licensor!!.clientToken)
    assertEquals("https://example.com/devices", entry.licensor!!.deviceManager)
  }

  @Test
  fun testAvailabilityReserved() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:6060606060",
              "title": "Reserved Book"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow",
                "properties": {
                  "availability": {
                    "state": "reserved",
                    "since": "2024-01-01T00:00:00Z",
                    "until": "2024-02-01T00:00:00Z"
                  }
                }
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is org.nypl.simplified.feeds.api.FeedAvailabilityHeld)
    val held = entry.availability as org.nypl.simplified.feeds.api.FeedAvailabilityHeld
    assertNotNull(held.startDate)
    assertNotNull(held.endDate)
  }

  @Test
  fun testAvailabilityUnavailable() {
    val json = """
      {
        "type": "feed",
        "metadata": {
          "title": "Test Feed"
        },
        "publications": [
          {
            "type": "publication",
            "metadata": {
              "identifier": "urn:isbn:7070707070",
              "title": "Unavailable Book"
            },
            "links": [
              {
                "href": "https://example.com/book.epub",
                "type": "application/epub+zip",
                "rel": "http://librarysimplified.org/terms/rel/acquire/borrow",
                "properties": {
                  "availability": {
                    "state": "unavailable"
                  }
                }
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val converted = this.parseAndConvert(json)

    assertTrue(converted is FeedData)
    val feedData = converted as FeedData
    val entry = feedData.entries[0] as FeedEntryBook

    assertTrue(entry.availability is org.nypl.simplified.feeds.api.FeedAvailabilityHoldable)
  }
}
