package org.thepalaceproject.palace

import org.librarysimplified.documents.DocumentConfiguration
import org.librarysimplified.documents.DocumentConfigurationServiceType
import java.net.URI

class PalaceDocumentStoreConfiguration : DocumentConfigurationServiceType {

  override val privacyPolicy: DocumentConfiguration? =
    DocumentConfiguration(
      name = "privacy.html",
      remoteURI = URI.create("https://legal.palaceproject.io/Privacy%20Policy.html")
    )

  override val about: DocumentConfiguration? =
    null

  override val acknowledgements: DocumentConfiguration? =
    null

  override val eula: DocumentConfiguration? =
    DocumentConfiguration(
      "eula.html",
      URI.create("https://legal.palaceproject.io/End%20User%20License%20Agreement.html")
    )

  override val licenses: DocumentConfiguration? =
    DocumentConfiguration(
      "software-licenses.html",
      URI.create("https://legal.palaceproject.io/software-licenses.html")
    )

  override val faq: DocumentConfiguration? =
    null
}
