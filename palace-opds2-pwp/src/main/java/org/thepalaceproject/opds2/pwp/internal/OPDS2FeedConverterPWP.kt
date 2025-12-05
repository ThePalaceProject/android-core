package org.thepalaceproject.opds2.pwp.internal

import org.nypl.simplified.links.Link
import org.nypl.simplified.opds2.OPDS2Catalog
import org.nypl.simplified.opds2.OPDS2CatalogMetadata
import org.nypl.simplified.opds2.OPDS2Contributor
import org.nypl.simplified.opds2.OPDS2Feed
import org.nypl.simplified.opds2.OPDS2Group
import org.nypl.simplified.opds2.OPDS2Metadata
import org.nypl.simplified.opds2.OPDS2Name
import org.nypl.simplified.opds2.OPDS2Navigation
import org.nypl.simplified.opds2.OPDS2Publication
import org.nypl.simplified.opds2.OPDS2Title
import org.nypl.simplified.parser.api.ParseResult
import org.thepalaceproject.webpub.core.WPMCatalog
import org.thepalaceproject.webpub.core.WPMContributor
import org.thepalaceproject.webpub.core.WPMContributorOrString
import org.thepalaceproject.webpub.core.WPMFeed
import org.thepalaceproject.webpub.core.WPMGroup
import org.thepalaceproject.webpub.core.WPMLanguageMap
import org.thepalaceproject.webpub.core.WPMLink
import org.thepalaceproject.webpub.core.WPMLinkBasic
import org.thepalaceproject.webpub.core.WPMLinkTemplated
import org.thepalaceproject.webpub.core.WPMMetadata
import org.thepalaceproject.webpub.core.WPMPublication
import java.net.URI
import java.time.OffsetDateTime

object OPDS2FeedConverterPWP {

  fun convert(
    source: URI,
    feed: WPMFeed
  ): ParseResult<OPDS2Feed> {
    return ParseResult.succeed(
      this.mapFeed(source, feed)
    )
  }

  private fun mapFeed(
    source: URI,
    feed: WPMFeed
  ): OPDS2Feed {
    return OPDS2Feed(
      uri = source,
      metadata = this.mapMetadata(feed.metadata),
      navigation = this.mapNavigation(feed.navigation),
      publications = this.mapPublications(feed.publications),
      groups = mapGroups(feed.groups),
      links = this.mapLinks(feed.links),
      images = this.mapLinks(feed.images),
      catalogs = mapCatalogs(feed.catalogs)
    )
  }

  private fun mapCatalogs(catalogs: List<WPMCatalog>): List<OPDS2Catalog> {
    return catalogs.map { c -> mapCatalog(c) }
  }

  private fun mapCatalog(
    catalog: WPMCatalog
  ): OPDS2Catalog {
    return OPDS2Catalog(
      metadata = mapCatalogMetadata(catalog.metadata),
      links = mapLinks(catalog.links),
      images = mapLinks(catalog.images)
    )
  }

  private fun mapCatalogMetadata(
    metadata: WPMMetadata
  ): OPDS2CatalogMetadata {
    return OPDS2CatalogMetadata(
      identifier = metadata.identifier,
      title = this.mapTitle(metadata.title),
      subtitle = this.mapTitle(metadata.subtitle),
      modified = this.mapTime(metadata.modified),
      published = this.mapTime(metadata.published),
      description = this.mapDescription(metadata.description),
      languages = this.mapLanguages(metadata.languages),
      sortAs = this.mapSortAs(metadata.sortAs),
      author = this.mapAuthors(metadata.author),
    )
  }

  private fun mapGroups(
    groups: List<WPMGroup>
  ): List<OPDS2Group> {
    return groups.map { group -> mapGroup(group) }
  }

  private fun mapGroup(
    group: WPMGroup
  ): OPDS2Group {
    return OPDS2Group(
      metadata = mapMetadata(group.metadata),
      navigation = mapNavigation(group.navigation),
      publications = listOf(),
      links = listOf()
    )
  }

  private fun mapPublications(
    publications: List<WPMPublication>
  ): List<OPDS2Publication> {
    return publications.map { pub -> this.mapPublication(pub) }
  }

  private fun mapPublication(
    pub: WPMPublication
  ): OPDS2Publication {
    return OPDS2Publication(
      metadata = this.mapMetadata(pub.metadata),
      links = this.mapLinks(pub.links),
      readingOrder = this.mapLinks(pub.readingOrder),
      resources = this.mapLinks(pub.resources),
      tableOfContents = this.mapLinks(pub.toc),
      images = this.mapLinks(pub.images)
    )
  }

  private fun mapNavigation(
    navigation: List<WPMLink>
  ): OPDS2Navigation? {
    if (navigation.isEmpty()) {
      return null
    }
    return OPDS2Navigation(
      links = this.mapLinks(navigation)
    )
  }

  private fun mapLinks(
    links: List<WPMLink>
  ): List<Link> {
    return links.map { link -> this.mapLink(link) }
  }

  private fun mapLink(
    link: WPMLink
  ): Link {
    return when (link) {
      is WPMLinkBasic -> {
        Link.LinkBasic(
          href = link.href,
          type = link.type,
          relation = link.relation.firstOrNull(),
          title = link.title,
          height = link.height,
          width = link.width,
          duration = link.duration?.toDouble(),
          bitrate = link.bitrate?.toDouble()
        )
      }
      is WPMLinkTemplated -> {
        Link.LinkTemplated(
          href = link.href,
          type = link.type,
          relation = link.relation.firstOrNull(),
          title = link.title,
          height = link.height,
          width = link.width,
          duration = link.duration?.toDouble(),
          bitrate = link.bitrate?.toDouble()
        )
      }
    }
  }

  private fun mapMetadata(
    metadata: WPMMetadata
  ): OPDS2Metadata {
    return OPDS2Metadata(
      identifier = metadata.identifier,
      title = this.mapTitle(metadata.title),
      subtitle = this.mapTitle(metadata.subtitle),
      description = this.mapDescription(metadata.description),
      modified = this.mapTime(metadata.modified),
      published = this.mapTime(metadata.published),
      languages = this.mapLanguages(metadata.languages),
      sortAs = this.mapSortAs(metadata.sortAs),
      author = this.mapAuthors(metadata.author)
    )
  }

  private fun mapAuthors(
    author: List<WPMContributorOrString>
  ): List<OPDS2Contributor> {
    return author.map { item ->
      when (item) {
        is WPMContributor -> {
          OPDS2Contributor(
            name = OPDS2Name(item.name ?: "")
          )
        }
        is WPMContributorOrString.WPMContributorString -> {
          OPDS2Contributor(
            name = OPDS2Name(item.value)
          )
        }
      }
    }
  }

  private fun mapSortAs(
    sortAs: WPMLanguageMap?
  ): String? {
    return when (sortAs) {
      is WPMLanguageMap.Mapped -> null
      is WPMLanguageMap.Scalar -> sortAs.value
      null -> null
    }
  }

  private fun mapLanguages(languages: List<String>): List<String> {
    return languages
  }

  private fun mapTime(t: OffsetDateTime?): OffsetDateTime? {
    return t
  }

  private fun mapDescription(description: String): String {
    return description
  }

  private fun mapTitle(
    title: WPMLanguageMap?
  ): OPDS2Title {
    return when (title) {
      is WPMLanguageMap.Mapped -> {
        OPDS2Title(byLanguage = title.byLanguage)
      }
      is WPMLanguageMap.Scalar -> {
        OPDS2Title(title = title.value)
      }
      null -> {
        OPDS2Title("")
      }
    }
  }
}
