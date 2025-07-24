import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.nypl.simplified.accounts.api.AccountProviderType
import org.nypl.simplified.accounts.api.AccountReadableType
import org.nypl.simplified.ui.accounts.AccountComparator

class AccountComparatorTest {

  private val expected = arrayListOf(
    mockAccount("Alameda County Library"),
    mockAccount("Berkeley Public Library"),
    mockAccount("The New York Public Library"),
    mockAccount("San Francisco Public Library"),
    mockAccount("Yolo County Library")
  )

  private val comparator = AccountComparator()

  @Test
  fun testSortedWith() {
    val shuffled = expected.shuffled()
    val actual = shuffled.sortedWith(comparator)
    assertEquals(expected, actual)
  }
}

private fun mockAccount(
  displayName: String
): AccountReadableType {
  val mockProvider =
    Mockito.mock(AccountProviderType::class.java)
  Mockito.`when`(mockProvider.displayName)
    .thenReturn(displayName)

  val mockAccount =
    Mockito.mock(AccountReadableType::class.java)
  Mockito.`when`(mockAccount.provider)
    .thenReturn(mockProvider)
  Mockito.`when`(mockAccount.toString())
    .thenReturn(displayName)

  return mockAccount
}
