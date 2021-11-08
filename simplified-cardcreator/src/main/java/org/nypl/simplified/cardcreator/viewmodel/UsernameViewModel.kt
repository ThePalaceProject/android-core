package org.nypl.simplified.cardcreator.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.nypl.simplified.cardcreator.model.Username
import org.nypl.simplified.cardcreator.model.ValidateUsernameResponse
import org.nypl.simplified.cardcreator.network.CardCreatorService
import org.nypl.simplified.cardcreator.utils.Channel

class UsernameViewModel(
  private val cardCreatorService: CardCreatorService
) : ViewModel() {

  val pendingRequest = MutableLiveData(false)

  val validateUsernameResponse = Channel<ValidateUsernameResponse>()

  fun validateUsername(username: String) {
    viewModelScope.launch {
      pendingRequest.value = true
      val response = cardCreatorService.validateUsername(Username(username))
      validateUsernameResponse.send(response)
      pendingRequest.value = false
    }
  }
}
