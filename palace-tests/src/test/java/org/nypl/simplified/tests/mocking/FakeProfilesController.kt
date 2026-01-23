package org.nypl.simplified.tests.mocking

import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FluentFuture
import io.reactivex.Observable
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountID
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.books.api.BookID
import org.nypl.simplified.feeds.api.Feed
import org.nypl.simplified.profiles.api.ProfileCreationEvent
import org.nypl.simplified.profiles.api.ProfileDeletionEvent
import org.nypl.simplified.profiles.api.ProfileDescription
import org.nypl.simplified.profiles.api.ProfileEvent
import org.nypl.simplified.profiles.api.ProfileID
import org.nypl.simplified.profiles.api.ProfileReadableType
import org.nypl.simplified.profiles.api.ProfileUpdated
import org.nypl.simplified.profiles.api.ProfilesDatabaseType
import org.nypl.simplified.profiles.controller.api.ProfileAccountLoginRequest
import org.nypl.simplified.profiles.controller.api.ProfileFeedRequest
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.taskrecorder.api.TaskResult
import java.net.URI
import java.util.SortedMap

class FakeProfilesController(fakeProfilesDatabase: FakeProfilesDatabase) : ProfilesControllerType {

  override fun profiles(): SortedMap<ProfileID, ProfileReadableType> {
    TODO("Not yet implemented")
  }

  override fun profileAnonymousEnabled(): ProfilesDatabaseType.AnonymousProfileEnabled {
    TODO("Not yet implemented")
  }

  override fun profileCurrent(): ProfileReadableType {
    TODO("Not yet implemented")
  }

  override fun profileAnyIsCurrent(): Boolean {
    TODO("Not yet implemented")
  }

  override fun profileEvents(): Observable<ProfileEvent> {
    TODO("Not yet implemented")
  }

  override fun profileCreate(
    displayName: String,
    accountProvider: AccountProviderType,
    descriptionUpdate: (ProfileDescription) -> ProfileDescription
  ): FluentFuture<ProfileCreationEvent> {
    TODO("Not yet implemented")
  }

  override fun profileDelete(profileID: ProfileID): FluentFuture<ProfileDeletionEvent> {
    TODO("Not yet implemented")
  }

  override fun profileSelect(profileID: ProfileID): FluentFuture<Unit> {
    TODO("Not yet implemented")
  }

  override fun profileAccountLogin(request: ProfileAccountLoginRequest): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun profileAccountCreate(provider: URI): FluentFuture<TaskResult<AccountType>> {
    TODO("Not yet implemented")
  }

  override fun profileAccountCreateCustomOPDS(opdsFeed: URI): FluentFuture<TaskResult<AccountType>> {
    TODO("Not yet implemented")
  }

  override fun profileAccountCreateOrReturnExisting(provider: URI): FluentFuture<TaskResult<AccountType>> {
    TODO("Not yet implemented")
  }

  override fun profileAccountDeleteByProvider(provider: URI): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun profileAccountFindByProvider(provider: URI): AccountType {
    TODO("Not yet implemented")
  }

  override fun accountEvents(): Observable<AccountEvent> {
    TODO("Not yet implemented")
  }

  override fun profileCurrentlyUsedAccountProviders(): ImmutableList<AccountProviderType> {
    TODO("Not yet implemented")
  }

  override fun profileAccountLogout(accountID: AccountID): FluentFuture<TaskResult<Unit>> {
    TODO("Not yet implemented")
  }

  override fun profileUpdate(update: (ProfileDescription) -> ProfileDescription): FluentFuture<ProfileUpdated> {
    TODO("Not yet implemented")
  }

  override fun profileUpdateFor(
    profile: ProfileID,
    update: (ProfileDescription) -> ProfileDescription
  ): FluentFuture<ProfileUpdated> {
    TODO("Not yet implemented")
  }

  override fun profileFeed(request: ProfileFeedRequest): FluentFuture<Feed.FeedWithoutGroups> {
    TODO("Not yet implemented")
  }

  override fun profileAccountForBook(bookID: BookID): AccountType {
    TODO("Not yet implemented")
  }
}
