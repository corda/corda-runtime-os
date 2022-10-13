package net.corda.membership.impl.read.reader

import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class NotaryLookupServiceImplTest {
    private val carolPublicKey = mock<PublicKey>()
    private val bobPublicKey = mock<PublicKey>()
    private val members = listOf(
        createMemberInfo(null),
        createMemberInfo(null),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Alice"),
                null,
                emptyList()
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Alice"),
                null,
                emptyList()
            )
        ),
        createMemberInfo(null),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Bob"),
                null,
                listOf(
                    mock {
                        on { publicKey } doReturn bobPublicKey
                    }
                )
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Bob"),
                "net.corda.plugin.Bob",
                emptyList()
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Bob"),
                null,
                emptyList()
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Alice"),
                "net.corda.plugin.Alice",
                emptyList()
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Carol"),
                null,
                listOf(
                    mock {
                        on { publicKey } doReturn carolPublicKey
                    }
                )
            )
        ),
        createMemberInfo(
            MemberNotaryDetails(
                MemberX500Name.parse("C=GB, L=London, O=Alice"),
                null,
                emptyList()
            )
        ),
    )
    private val reader = mock<MembershipGroupReader> {
        on { lookup() } doReturn members
    }
    private val service = NotaryLookupServiceImpl(reader)

    @Test
    fun `notaryServices return all the valid notary services`() {
        assertThat(service.notaryServices).hasSize(2)
            .anyMatch {
                it.party == MemberX500Name.parse("C=GB, L=London, O=Alice") &&
                    it.pluginClass == "net.corda.plugin.Alice"
            }
            .anyMatch {
                it.party == MemberX500Name.parse("C=GB, L=London, O=Bob") &&
                    it.pluginClass == "net.corda.plugin.Bob"
            }
    }

    @Test
    fun `lookup return null for unknown service`() {
        assertThat(service.lookup(MemberX500Name.parse("C=GB, L=London, O=Dave"))).isNull()
    }

    @Test
    fun `lookup return null for service without a plugin`() {
        assertThat(service.lookup(MemberX500Name.parse("C=GB, L=London, O=Carol"))).isNull()
    }

    @Test
    fun `lookup return correct details when available`() {
        assertThat(service.lookup(MemberX500Name.parse("C=GB, L=London, O=Alice")))
            .isEqualTo(
                NotaryInfoImpl(
                    party = MemberX500Name.parse("C=GB, L=London, O=Alice"),
                    pluginClass = "net.corda.plugin.Alice"
                )
            )
    }

    @Test
    fun `lookup by public key returns null for unknown public key`() {
        val key = mock<PublicKey>()

        assertThat(service.lookup(key)).isNull()
    }

    @Test
    fun `lookup by public key returns null when plugin class is missing`() {
        assertThat(service.lookup(carolPublicKey)).isNull()
    }

    @Test
    fun `lookup by public key returns correct details when plugin class is found`() {
        assertThat(service.lookup(bobPublicKey)).isEqualTo(
            NotaryInfoImpl(
                party = MemberX500Name.parse("C=GB, L=London, O=Bob"),
                pluginClass = "net.corda.plugin.Bob"
            )
        )
    }

    @Test
    fun `isNotary returns true when the virtual node is a notary`() {
        val name = MemberX500Name.parse("C=GB, L=London, O=Vera")
        val memberInfo = createMemberInfo(mock())
        whenever(reader.lookup(name)).doReturn(memberInfo)

        assertThat(service.isNotary(name)).isTrue
    }

    @Test
    fun `isNotary returns false when the virtual node is not a notary`() {
        val name = MemberX500Name.parse("C=GB, L=London, O=Vera")
        val memberInfo = createMemberInfo(null)
        whenever(reader.lookup(name)).doReturn(memberInfo)

        assertThat(service.isNotary(name)).isFalse
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
