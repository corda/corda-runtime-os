package net.corda.membership.lib

import net.corda.membership.lib.MemberInfoExtension.Companion.HISTORIC_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_STATIC_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.cpiInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.historicSessionInitiationKeys
import net.corda.membership.lib.MemberInfoExtension.Companion.historicSessionKeyHashes
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.lib.MemberInfoExtension.Companion.isStaticMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.utilities.parseOrNull
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MemberInfoExtensionTest {
    private val memberContext: MemberContext = mock()
    private val mgmContext: MGMContext = mock()
    private val memberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberContext
        on { mgmProvidedContext } doReturn mgmContext
    }

    @Test
    fun `CPI Info can be parsed from member context`() {
        val cpiName = "cpi"
        val cpiVersion = "1.3"
        val cpiSignerHash = "ALG:A1B2C3D4"
        whenever(
            memberContext.parse<String>(MEMBER_CPI_NAME)
        ) doReturn cpiName

        whenever(
            memberContext.parse<String>(MEMBER_CPI_VERSION)
        ) doReturn cpiVersion

        whenever(
            memberContext.parse<String>(MEMBER_CPI_SIGNER_HASH)
        ) doReturn cpiSignerHash

        val result = assertDoesNotThrow {
            memberInfo.cpiInfo
        }

        assertThat(result.name).isEqualTo(cpiName)
        assertThat(result.version).isEqualTo(cpiVersion)
        assertThat(result.signerSummaryHash.toString()).isEqualTo(cpiSignerHash)
    }

    @Test
    fun `Software version can be parsed from member context`() {
        val softwareVersion = "software-version"
        whenever(
            memberContext.parse<String>(SOFTWARE_VERSION)
        ) doReturn softwareVersion

        val result = assertDoesNotThrow {
            memberInfo.softwareVersion
        }

        assertThat(result).isEqualTo(softwareVersion)
    }

    @Nested
    inner class StaticNetworkMGM {
        @Test
        fun `Can see if member is a static network MGM`() {
            whenever(mgmContext.parseOrNull<Boolean>(IS_STATIC_MGM)) doReturn true

            val result = assertDoesNotThrow { memberInfo.isStaticMgm }

            assertThat(result).isTrue
        }

        @Test
        fun `Can see if member is not a static network MGM when specified`() {
            whenever(mgmContext.parseOrNull<Boolean>(IS_STATIC_MGM)) doReturn false

            val result = assertDoesNotThrow { memberInfo.isStaticMgm }

            assertThat(result).isFalse
        }

        @Test
        fun `Can see if member is not a static network MGM when not specified`() {
            whenever(mgmContext.parseOrNull<Boolean>(IS_STATIC_MGM)) doReturn null

            val result = assertDoesNotThrow { memberInfo.isStaticMgm }

            assertThat(result).isFalse
        }
    }

    @Nested
    inner class IsNotary {

        @Test
        fun `Member is notary if notary role is set`() {
            whenever(memberContext.entries).doReturn(mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries)

            val result = assertDoesNotThrow { memberInfo.isNotary() }

            assertThat(result).isTrue
        }

        @Test
        fun `Member is not a notary if notary role is not set`() {
            whenever(memberContext.entries).doReturn(emptyMap<String, String>().entries)

            val result = assertDoesNotThrow { memberInfo.isNotary() }

            assertThat(result).isFalse
        }

        @Test
        fun `Member is not a notary if a non-notary role is set`() {
            whenever(memberContext.entries).doReturn(mapOf("$ROLES_PREFIX.0" to "some other role").entries)

            val result = assertDoesNotThrow { memberInfo.isNotary() }

            assertThat(result).isFalse
        }

        @Test
        fun `Member is notary if one of the roles set is a notary role`() {
            whenever(memberContext.entries).doReturn(
                mapOf(
                    "$ROLES_PREFIX.0" to "fake role",
                    "$ROLES_PREFIX.1" to NOTARY_ROLE,
                ).entries
            )

            val result = assertDoesNotThrow { memberInfo.isNotary() }

            assertThat(result).isTrue
        }
    }

    @Nested
    inner class HistoricSessionKeyTest {
        @Test
        fun `empty list returned if historic key information is not available`() {
            whenever(memberContext.entries).doReturn(emptyMap<String, String>().entries)

            assertThat(memberInfo.historicSessionInitiationKeys).isEmpty()
        }

        @Test
        fun `empty list returned for key hashes if historic key information is not available`() {
            whenever(memberContext.entries).doReturn(emptyMap<String, String>().entries)

            assertThat(memberInfo.historicSessionKeyHashes).isEmpty()
        }

        @Test
        fun `single key returned when historic key information is available`() {
            val mockKeyHashes: List<PublicKey> = listOf(mock())
            whenever(
                memberContext.parseList(eq(HISTORIC_SESSION_KEYS), eq(PublicKey::class.java))
            ).doReturn(mockKeyHashes)

            assertThat(memberInfo.historicSessionInitiationKeys).containsExactlyElementsOf(mockKeyHashes)
        }

        @Test
        fun `single key hash returned when historic key information is available`() {
            val mockKeyHashes: Set<SecureHash> = setOf(mock())
            whenever(
                memberContext.parseSet(eq(HISTORIC_SESSION_KEYS), eq(SecureHash::class.java))
            ).doReturn(mockKeyHashes)

            assertThat(memberInfo.historicSessionKeyHashes).containsExactlyElementsOf(mockKeyHashes)
        }

        @Test
        fun `multiple keys returned when historic key information is available`() {
            val mockKeyHashes: List<PublicKey> = listOf(mock(), mock(), mock(), mock())
            whenever(
                memberContext.parseList(eq(HISTORIC_SESSION_KEYS), eq(PublicKey::class.java))
            ).doReturn(mockKeyHashes)

            assertThat(memberInfo.historicSessionInitiationKeys).containsExactlyElementsOf(mockKeyHashes)
        }

        @Test
        fun `multiple key hashes returned when historic key information is available`() {
            val mockKeyHashes: Set<SecureHash> = setOf(mock(), mock(), mock(), mock())
            whenever(
                memberContext.parseSet(eq(HISTORIC_SESSION_KEYS), eq(SecureHash::class.java))
            ).doReturn(mockKeyHashes)

            assertThat(memberInfo.historicSessionKeyHashes).containsExactlyElementsOf(mockKeyHashes)
        }

        @Test
        fun `key hashes calculated and returned when historic key information is available but hash is not`() {
            whenever(
                memberContext.parseSet(eq(HISTORIC_SESSION_KEYS), eq(SecureHash::class.java))
            ).doReturn(emptySet())

            val mockKey: PublicKey = mock {
                on { encoded } doReturn "my-key".toByteArray()
            }
            whenever(
                memberContext.parseList(eq(HISTORIC_SESSION_KEYS), eq(PublicKey::class.java))
            ).doReturn(listOf(mockKey))

            assertThat(memberInfo.historicSessionKeyHashes).hasSize(1)
        }

        @Test
        fun `key hashes calculated and returned when multiple historic key information is available but hashes are not`() {
            whenever(
                memberContext.parseSet(eq(HISTORIC_SESSION_KEYS), eq(SecureHash::class.java))
            ).doReturn(emptySet())

            val mockKey1: PublicKey = mock {
                on { encoded } doReturn "my-key-1".toByteArray()
            }
            val mockKey2: PublicKey = mock {
                on { encoded } doReturn "my-key-2".toByteArray()
            }
            whenever(
                memberContext.parseList(eq(HISTORIC_SESSION_KEYS), eq(PublicKey::class.java))
            ).doReturn(listOf(mockKey1, mockKey2))

            assertThat(memberInfo.historicSessionKeyHashes).hasSize(2)
        }

        @Test
        fun `key hashes empty when historic key information is not available and hash is not either`() {
            whenever(
                memberContext.parseSet(eq(HISTORIC_SESSION_KEYS), eq(SecureHash::class.java))
            ).doReturn(emptySet())

            whenever(
                memberContext.parseList(eq(HISTORIC_SESSION_KEYS), eq(PublicKey::class.java))
            ).doReturn(emptyList())

            assertThat(memberInfo.historicSessionKeyHashes).isEmpty()
        }
    }
}
