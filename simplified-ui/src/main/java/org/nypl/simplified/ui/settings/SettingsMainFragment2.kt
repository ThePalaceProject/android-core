package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.documents.DocumentType
import org.librarysimplified.ui.R
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.listeners.api.FragmentListenerType
import org.nypl.simplified.listeners.api.fragmentListeners
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.threads.UIThread
import org.nypl.simplified.ui.accounts.AccountListEvent

class SettingsMainFragment2 : Fragment() {

  // XXX: Remove event based navigation when the catalog is rewritten
  private val settingsEventListener: FragmentListenerType<SettingsMainEvent>
    by this.fragmentListeners()
  private val settingsAccountListener: FragmentListenerType<AccountListEvent>
    by this.fragmentListeners()

  private var subscriptions = CompositeDisposable()

  private lateinit var about: View
  private lateinit var accounts: RecyclerView
  private lateinit var adapter: SettingsAccountAdapter
  private lateinit var addLibrary: TextView
  private lateinit var debug: View
  private lateinit var debugEnd: View
  private lateinit var eula: View
  private lateinit var license: View
  private lateinit var privacy: View
  private lateinit var version: TextView

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view =
      inflater.inflate(R.layout.settings_main, container, false)

    this.accounts =
      view.findViewById(R.id.settingsLibraryList)
    this.about =
      view.findViewById(R.id.settingsAbout)
    this.privacy =
      view.findViewById(R.id.settingsPrivacy)
    this.eula =
      view.findViewById(R.id.settingsEULA)
    this.license =
      view.findViewById(R.id.settingsLicense)
    this.debug =
      view.findViewById(R.id.settingsDebug)
    this.debugEnd =
      view.findViewById(R.id.settingsDebugEnd)
    this.version =
      view.findViewById(R.id.settingsVersion)
    this.addLibrary =
      view.findViewById(R.id.settingsAddLibrary)

    this.accounts.layoutManager = LinearLayoutManager(view.context)
    this.accounts.setHasFixedSize(true)
    (this.accounts.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

    val documents = SettingsModel.documents
    this.createDocumentView(
      document = documents.about,
      view = this.about,
      titleId = R.string.settingsAbout,
    ) { v, id, doc ->
      SettingsMainEvent.OpenAbout(
        title = v.context.getString(id),
        url = doc.readableURL.toString()
      )
    }
    this.createDocumentView(
      document = documents.privacyPolicy,
      view = this.privacy,
      titleId = R.string.settingsPrivacy,
    ) { v, id, doc ->
      SettingsMainEvent.OpenPrivacy(
        title = v.context.getString(id),
        url = doc.readableURL.toString()
      )
    }
    this.createDocumentView(
      document = documents.eula,
      view = this.eula,
      titleId = R.string.settingsEULA,
    ) { v, id, doc ->
      SettingsMainEvent.OpenEULA(
        title = v.context.getString(id),
        url = doc.readableURL.toString()
      )
    }
    this.createDocumentView(
      document = documents.licenses,
      view = this.license,
      titleId = R.string.settingsLicense,
    ) { v, id, doc ->
      SettingsMainEvent.OpenLicense(
        title = v.context.getString(id),
        url = doc.readableURL.toString()
      )
    }

    this.version.setOnClickListener {
      SettingsModel.onClickVersion()
    }
    this.debug.setOnClickListener {
      this.settingsEventListener.post(SettingsMainEvent.OpenDebugOptions)
    }
    this.addLibrary.setOnClickListener {
      this.settingsAccountListener.post(AccountListEvent.AddAccount)
    }
    return view
  }

  private fun createDocumentView(
    document: DocumentType?,
    view: View,
    titleId: Int,
    eventFactory: (View, Int, DocumentType) -> SettingsMainEvent
  ) {
    if (document != null) {
      val event = eventFactory.invoke(view, titleId, document)
      view.setOnClickListener {
        this.settingsEventListener.post(event)
      }
    } else {
      view.isEnabled = false
    }
  }

  override fun onStart() {
    super.onStart()

    this.adapter = SettingsAccountAdapter(
      isAccountSelected = this::isAccountSelected,
      onSelectAccount = this::onSelectAccount,
      onOpenAccountSettings = this::onOpenAccountSettings
    )
    this.accounts.adapter = this.adapter

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(
      SettingsModel.accountEvents.subscribe {
        this.onAccountEvent()
      }
    )
    this.subscriptions.add(
      SettingsModel.profileEvents.ofType(ProfileUpdated::class.java)
        .subscribe { _ -> this.onProfileUpdated() }
    )

    this.version.text = this.formatVersion()
    this.reloadAccountList()

    this.showOrHideDebug()
  }

  @UiThread
  private fun onOpenAccountSettings(
    item: AccountType
  ) {
    this.settingsAccountListener.post(
      AccountListEvent.AccountSelected(
        accountID = item.id,
        barcode = null,
        comingFromDeepLink = false
      )
    )
  }

  @UiThread
  private fun onSelectAccount(
    item: AccountType
  ) {
    SettingsModel.setMostRecentAccount(item.id)
  }

  @UiThread
  private fun isAccountSelected(
    item: AccountType
  ): Boolean {
    return SettingsModel.isAccountSelected(item)
  }

  @UiThread
  private fun onProfileUpdated() {
    UIThread.checkIsUIThread()

    this.showOrHideDebug()
  }

  @UiThread
  private fun showOrHideDebug() {
    if (SettingsModel.showDebugSettings) {
      this.debug.visibility = View.VISIBLE
      this.debugEnd.visibility = View.VISIBLE
    } else {
      this.debug.visibility = View.GONE
      this.debugEnd.visibility = View.GONE
    }
  }

  private fun formatVersion(): String {
    return try {
      val context =
        this.requireContext()
      val pkgManager =
        context.packageManager
      val pkgInfo =
        pkgManager.getPackageInfo(context.packageName, 0)
      val versionName =
        SettingsModel.buildConfig.simplifiedVersion
      val commit =
        SettingsModel.buildConfig.vcsCommit

      "Palace $versionName (${pkgInfo.versionCode}) [$commit]"
    } catch (e: Throwable) {
      "Unknown"
    }
  }

  @UiThread
  private fun onAccountEvent() {
    UIThread.checkIsUIThread()

    this.reloadAccountList()
  }

  @UiThread
  private fun reloadAccountList() {
    this.adapter.submitList(
      SettingsModel.profilesController.profileCurrent()
        .accounts()
        .values
        .sortedBy { a -> a.provider.displayName }
        .toList()
    )
  }

  override fun onStop() {
    super.onStop()

    this.adapter.submitList(listOf())
    this.subscriptions.dispose()
  }
}
