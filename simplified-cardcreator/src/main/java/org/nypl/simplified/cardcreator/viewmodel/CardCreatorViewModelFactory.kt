package org.nypl.simplified.cardcreator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.nypl.simplified.cardcreator.network.CardCreatorService

class CardCreatorViewModelFactory(
  private val cardCreatorService: CardCreatorService
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel?> create(modelClass: Class<T>): (T & Any) {
    return when (modelClass) {
      CardCreatorViewModel::class.java -> {
        CardCreatorViewModel(cardCreatorService) as (T & Any)
      }
      AddressViewModel::class.java -> {
        AddressViewModel(cardCreatorService) as (T & Any)
      }
      DependentEligibilityViewModel::class.java -> {
        DependentEligibilityViewModel(cardCreatorService) as (T & Any)
      }
      PatronViewModel::class.java -> {
        PatronViewModel(cardCreatorService) as (T & Any)
      }
      UsernameViewModel::class.java -> {
        UsernameViewModel(cardCreatorService) as (T & Any)
      }
      else -> {
        throw IllegalStateException("Can't create values of $modelClass")
      }
    }
  }
}
