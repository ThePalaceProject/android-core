package org.nypl.simplified.tests.adobe

import com.io7m.verona.core.Version
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.nypl.simplified.adobe.extensions.AdobeDRMExtensions

class AdobeDRMExtensionsTest {

  /**
   * An activation with no recorded provider version was performed with software old enough
   * to predate the version stamp, and must be treated as stale.
   */

  @Test
  fun testActivationIsStaleNoVersion() {
    assertTrue(AdobeDRMExtensions.activationIsStale(null))
  }

  /**
   * An activation recorded by a pre-migration provider binary must be treated as stale.
   */

  @Test
  fun testActivationIsStaleOldVersion() {
    assertTrue(AdobeDRMExtensions.activationIsStale(Version.of(4, 99, 99)))
  }

  /**
   * An activation recorded by the current provider binary is not stale.
   */

  @Test
  fun testActivationIsNotStaleCurrentVersion() {
    assertFalse(AdobeDRMExtensions.activationIsStale(Version.of(5, 0, 0)))
    assertFalse(AdobeDRMExtensions.activationIsStale(AdobeDRMExtensions.versionCurrent()))
  }
}
