package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RegistrationsLookupTest {

    @Test
    fun testIsVnodeRegistrationApprovedEmptyList() {
        val client = CordaRestClient.createHttpClient()
        client.memberRegistrationClient = mock {
            on { it.getMembershipHoldingidentityshorthash(any()) } doReturn emptyList()
        }

        val result = RegistrationsLookup(client).isVnodeRegistrationApproved(
            ShortHash.parse("123456789123")
        )
        Assertions.assertThat(result).isFalse
    }
}
