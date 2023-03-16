package net.corda.virtualnode.rest.factories.impl

import net.corda.data.virtualnode.VirtualNodeAsynchronousRequest
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.rest.security.RestContextProvider
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequest
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity

class RequestFactoryImplTest {
    private val now = Instant.now()
    private val actor = "user1"
    private val restContextProvider = mock<RestContextProvider>().apply { whenever(principal).thenReturn(actor) }
    private val clock = mock<Clock>().apply { whenever(instant()).thenReturn(now) }
    private val target = RequestFactoryImpl(restContextProvider, clock)

    @Test
    fun `create holding identity`() {
        val alice = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "g1"

        val request = CreateVirtualNodeRequest(
            alice,
            "cpics",
            null,
            null,
            null,
            null,
            null,
            null,
        )

        val expectedHoldingIdentity = HoldingIdentity(MemberX500Name.parse(alice), groupId)

        val result = target.createHoldingIdentity(groupId, request)

        assertThat(result).isEqualTo(expectedHoldingIdentity)
    }

    @Test
    fun `create virtual node async request is returned`() {
        val alice = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        val groupId = "g1"
        val cpiChecksum = "cpics"
        val vaultDdlConnection = "vddl"
        val vaultDmlConnection = "vdml"
        val cryptoDdlConnection = "cddl"
        val cryptoDmlConnection = "cdml"
        val uniquenessDdlConnection = "uddl"
        val uniquenessDmlConnection = "udml"

        val request = CreateVirtualNodeRequest(
            alice,
            cpiChecksum,
            vaultDdlConnection,
            vaultDmlConnection,
            cryptoDdlConnection,
            cryptoDmlConnection,
            uniquenessDdlConnection,
            uniquenessDmlConnection,
        )

        val holdingIdentity = HoldingIdentity(MemberX500Name.parse(alice), groupId)

        val expectedHoldingIdentity = AvroHoldingIdentity(alice, groupId)

        val expectedCreateNodeRequest = VirtualNodeCreateRequest().apply {
            this.holdingId = expectedHoldingIdentity
            this.cpiFileChecksum = cpiChecksum
            this.vaultDdlConnection = vaultDdlConnection
            this.vaultDmlConnection = vaultDmlConnection
            this.cryptoDdlConnection = cryptoDdlConnection
            this.cryptoDmlConnection = cryptoDmlConnection
            this.uniquenessDdlConnection = uniquenessDdlConnection
            this.uniquenessDmlConnection = uniquenessDmlConnection
            this.updateActor = actor
        }

        val expectedAsyncRequest = VirtualNodeAsynchronousRequest().apply {
            this.requestId = holdingIdentity.shortHash.toString()
            this.request = expectedCreateNodeRequest
            this.timestamp = now
        }

        val result = target.createVirtualNodeRequest(holdingIdentity, request)
        assertThat(result).isEqualTo(expectedAsyncRequest)
    }
}
