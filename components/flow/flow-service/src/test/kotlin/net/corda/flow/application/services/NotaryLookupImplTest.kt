package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.fiber.FlowFiberService
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SignatureSpec.Companion.RSA_SHA512
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class NotaryLookupImplTest {
    private val alice = MemberX500Name.parse("O=Alice, L=LDN, C=GB")
    private val bob = MemberX500Name.parse("O=Bob, L=LDN, C=GB")
    private val carol = MemberX500Name.parse("O=Carol, L=LDN, C=GB")
    private val alicePublicKeyOne = mock<PublicKey>()
    private val alicePublicKeyTwo = mock<PublicKey>()
    private val alicePublicKeyCompose = mock<PublicKey>()
    private val carolPublicKeyOne = mock<PublicKey>()
    private val carolPublicKeyTwo = mock<PublicKey>()
    private val carolPublicKeyCompose = mock<PublicKey>()
    private val members = listOf(
        createMemberInfo(null),
        createMemberInfo(null),
        createMemberInfo(
            MemberNotaryDetails(
                alice,
                "net.corda.Plugin1",
                listOf(
                    MemberNotaryKey(
                        alicePublicKeyOne,
                        PublicKeyHash.calculate("1234".toByteArray()),
                        RSA_SHA512,
                    ),
                    MemberNotaryKey(
                        alicePublicKeyTwo,
                        PublicKeyHash.calculate("433".toByteArray()),
                        RSA_SHA512,
                    ),
                )
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                bob,
                null,
                emptyList()
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                carol,
                null,
                listOf(
                    MemberNotaryKey(
                        carolPublicKeyOne,
                        PublicKeyHash.calculate("456".toByteArray()),
                        RSA_SHA512,
                    )
                )
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                carol,
                "net.corda.Plugin2",
                listOf(
                    MemberNotaryKey(
                        carolPublicKeyTwo,
                        PublicKeyHash.calculate("456".toByteArray()),
                        RSA_SHA512,
                    )
                )
            )
        ),
    )
    private val groupReader = mock<MembershipGroupReader> {
        on { lookup() } doReturn members
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
    private val compositeKeyGenerator = mock<CompositeKeyGenerator> {
        on {
            create(
                listOf(
                    CompositeKeyNodeAndWeight(
                        alicePublicKeyOne,
                        1
                    ),
                    CompositeKeyNodeAndWeight(
                        alicePublicKeyTwo,
                        1
                    ),
                ),
                0
            )
        } doReturn alicePublicKeyCompose
        on {
            create(
                listOf(
                    CompositeKeyNodeAndWeight(
                        carolPublicKeyOne,
                        1
                    ),
                    CompositeKeyNodeAndWeight(
                        carolPublicKeyTwo,
                        1
                    ),
                ),
                0
            )
        } doReturn carolPublicKeyCompose
    }

    private val lookup = NotaryLookupImpl(flowFiberService, compositeKeyGenerator)

    @Test
    fun `notaryServices return all the notary services`() {
        val notaries = lookup.notaryServices

        assertThat(notaries).anySatisfy {
            assertThat(it.party).isEqualTo(alice)
            assertThat(it.pluginClass).isEqualTo("net.corda.Plugin1")
            assertThat(it.publicKey).isEqualTo(alicePublicKeyCompose)
        }.anySatisfy {
            assertThat(it.party).isEqualTo(carol)
            assertThat(it.pluginClass).isEqualTo("net.corda.Plugin2")
            assertThat(it.publicKey).isEqualTo(carolPublicKeyCompose)
        }.hasSize(2)
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
        val info = lookup.lookup(carol)

        assertThat(info?.pluginClass).isEqualTo("net.corda.Plugin2")
    }

    @Test
    fun `lookup by service name will return null when there is no plugin`() {
        val info = lookup.lookup(bob)

        assertThat(info).isNull()
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
