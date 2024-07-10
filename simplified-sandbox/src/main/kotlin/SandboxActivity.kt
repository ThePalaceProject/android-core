package org.librarysimplified.sandbox

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.io7m.jfunctional.Option.none
import org.librarysimplified.ui.catalog.CatalogFacetDialog
import org.librarysimplified.ui.catalog.CatalogFacetModel
import org.nypl.simplified.feeds.api.FeedFacet
import org.nypl.simplified.opds.core.OPDSFacet
import java.net.URI

class SandboxActivity : AppCompatActivity(R.layout.sandbox) {

  private lateinit var button: Button

  override fun onCreate(
    savedInstanceState: Bundle?
  ) {
    super.onCreate(savedInstanceState)

    this.button =
      this.findViewById(R.id.button)

    CatalogFacetModel.setFacets(
      mapOf(
        Pair("Sort By", listOf(
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Sort By",
              "author",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Sort By",
              "title",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Sort By",
              "recently added",
              none()
            ),
          )
        )),
        Pair("Format", listOf(
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Format",
              "ebooks",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Format",
              "audiobooks",
              none()
            ),
          ),
        )),
        Pair("Availability", listOf(
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Availability",
              "yours to keep",
              none()
            ),
          ),
        )),
        Pair("Distributor", listOf(
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Distributor",
              "biblioboard",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Distributor",
              "bibliotecha",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Distributor",
              "overdrive",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Distributor",
              "palace bookshelf",
              none()
            ),
          ),
          FeedFacet.FeedFacetOPDS(
            OPDSFacet(
              false,
              URI.create("https://www.example.com"),
              "Distributor",
              "palace marketplace",
              none()
            ),
          ),
        ))
      ),
      onSelectFacet = {

      }
    )
  }

  override fun onStart() {
    super.onStart()

    this.button.setOnClickListener {
      val dialog = CatalogFacetDialog()
      dialog.show(this.supportFragmentManager, "FACET_SELECTION")
    }
  }
}
