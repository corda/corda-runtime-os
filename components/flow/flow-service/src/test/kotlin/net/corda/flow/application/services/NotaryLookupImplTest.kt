package net.corda.flow.application.services

import net.corda.flow.application.services.impl.NotaryLookupImpl
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.v5.membership.NotaryInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class NotaryLookupImplTest {
    private val alice = MemberX500Name.parse("O=Alice, L=LDN, C=GB")
    private val bob = MemberX500Name.parse("O=Bob, L=LDN, C=GB")
    private val alicePublicKeyCompose = mock<PublicKey>()
    private val bobPublicKeyCompose = mock<PublicKey>()
    private val notaryServiceAlice: NotaryInfo = mock {
        on { name } doReturn alice
        on { pluginClass } doReturn "net.corda.Plugin1"
        on { publicKey } doReturn alicePublicKeyCompose
    }
    private val notaryServiceBob: NotaryInfo = mock {
        on { name } doReturn bob
        on { pluginClass } doReturn "net.corda.Plugin2"
        on { publicKey } doReturn bobPublicKeyCompose
    }
    private val notaries = listOf(
        notaryServiceAlice,
        notaryServiceBob
    )
    private val groupParameters: GroupParameters = mock {
        on { notaries } doReturn notaries
    }
    private val groupReader = mock<MembershipGroupReader> {
        on { groupParameters } doReturn groupParameters
    }
    private val context = mock<FlowFiberExecutionContext> {
        on { membershipGroupReader } doReturn groupReader
    }
    private val flowFiber = mock<FlowFiber> {
        on { getExecutionContext() } doReturn context
    }
    private val flowFiberService = mock<FlowFiberService> {
        on { getExecutingFiber() } doReturn flowFiber
    }

    private val lookup = NotaryLookupImpl(flowFiberService)

    @Test
    fun `notaryServices return all the notary services`() {
        val notaries = lookup.notaryServices.toList()

        assertThat(notaries).anySatisfy {
            assertThat(it.name).isEqualTo(alice)
            assertThat(it.pluginClass).isEqualTo("net.corda.Plugin1")
            assertThat(it.publicKey).isEqualTo(alicePublicKeyCompose)
        }.anySatisfy {
            assertThat(it.name).isEqualTo(bob)
            assertThat(it.pluginClass).isEqualTo("net.corda.Plugin2")
            assertThat(it.publicKey).isEqualTo(bobPublicKeyCompose)
        }.hasSize(2)
    }

    @Test
    fun `notaryServices returns empty list when group params are null`() {
        whenever(groupReader.groupParameters).thenReturn(null)
        assertThat(lookup.notaryServices).isEmpty()
    }

    @Test
    fun `isNotaryVirtualNode return true if the node is a virtual node`() {
        val member = createMemberInfo(
            MemberNotaryDetails(
                alice,
                "net.corda.Plugin1",
                emptyList()
            )
        )
        whenever(groupReader.lookup(alice)).thenReturn(member)

        assertThat(lookup.isNotaryVirtualNode(alice)).isTrue
    }

    @Test
    fun `isNotaryVirtualNode return false if the node is not a virtual node`() {
        val member = createMemberInfo(null)
        whenever(groupReader.lookup(alice)).thenReturn(member)

        assertThat(lookup.isNotaryVirtualNode(alice)).isFalse
    }

    @Test
    fun `isNotaryVirtualNode return false if the there is no such node`() {
        whenever(groupReader.lookup(alice)).thenReturn(null)

        assertThat(lookup.isNotaryVirtualNode(alice)).isFalse
    }

    @Test
    fun `lookup by service name will return the correct information`() {
        val info = lookup.lookup(bob)

        assertThat(info?.pluginClass).isEqualTo("net.corda.Plugin2")
    }

    @Test
    fun `lookup by service name will return null when the service name is unknown`() {
        val info = lookup.lookup(MemberX500Name.parse("O=Zena, L=LDN, C=GB"))

        assertThat(info).isNull()
    }

    private fun createMemberInfo(notary: MemberNotaryDetails?): MemberInfo {
        val context: MemberContext = if (notary == null) {
            mock {
                on { entries } doReturn emptySet()
            }
        } else {
            val map = mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE)
            mock {
                on { entries } doReturn map.entries
                on { parse("corda.notary", MemberNotaryDetails::class.java) } doReturn notary
            }
        }
        return mock {
            on { memberProvidedContext } doReturn context
        }
    }
}
