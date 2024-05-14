package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.restclient.CordaRestClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ExportGroupPolicyFromMgmTest {

    @Test
    fun testExportPolicy() {
        val client = CordaRestClient.createHttpClient(baseUrl = "https://localhost:8888")
        client.mgmClient = mock {
            on(it.getMgmHoldingidentityshorthashInfo(any())) doReturn """
                {
                "groupId" : "af04c544-09b4-4f40-a59f-01241fe50e74"
                }
            """.trimIndent()
        }
        val policy = ExportGroupPolicyFromMgm(client).exportPolicy(
            holdingIdentityShortHash = ShortHash.parse("123456789012")
        )
        assertThat(policy).contains("groupId")
    }
}
