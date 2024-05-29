package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.apis.MGMApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ExportGroupPolicyFromMgmTest {

    @Test
    fun testExportPolicy() {
        val mockedMgmApi: MGMApi = mock {
            on(it.getMgmHoldingidentityshorthashInfo(any())) doReturn """
                {
                "groupId" : "af04c544-09b4-4f40-a59f-01241fe50e74"
                }
            """.trimIndent()
        }
        val client = CordaRestClient.createHttpClient(mgmClient = mockedMgmApi)
        val policy = ExportGroupPolicyFromMgm(client).exportPolicy(
            holdingIdentityShortHash = ShortHash.parse("123456789012")
        )
        assertThat(policy).contains("groupId")
    }
}
