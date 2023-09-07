package org.nypl.simplified.tests.overdrive

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.main.MainOverdriveSecretService

class MainOverdriveSecretServiceTest {

  @Test
  fun create_withKeyAndSecret_succeeds() {
    val secretService = MainOverdriveSecretService.create(
      """
      foo.value.a = something
      foo.value.b = another
      overdrive.prod.client.key = hello
      overdrive.prod.client.secret = world
      """.trimIndent().byteInputStream()
    )

    Assertions.assertEquals("hello", secretService.clientKey)
    Assertions.assertEquals("world", secretService.clientPass)
  }

  @Test
  fun create_withoutKeyAndSecret_succeeds() {
    val secretService = MainOverdriveSecretService.create(
      """
      foo.value.a = something
      foo.value.b = another
      """.trimIndent().byteInputStream()
    )

    Assertions.assertEquals(null, secretService.clientKey)
    Assertions.assertEquals(null, secretService.clientPass)
  }
}
