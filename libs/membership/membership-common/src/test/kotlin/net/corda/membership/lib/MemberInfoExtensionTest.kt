package net.corda.membership.lib

import net.corda.membership.lib.MemberInfoExtension.Companion.IS_STATIC_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.PRE_AUTH_TOKEN
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.cpiInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.lib.MemberInfoExtension.Companion.isStaticMgm
import net.corda.membership.lib.MemberInfoExtension.Companion.preAuthToken
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.utilities.parse
import net.corda.utilities.parseOrNull
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
import java.util.UUID

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

    @Test
    fun `Pre-auth token can be parsed from member context is present`() {
        val preAuthToken = UUID(0, 1)
        whenever(
            memberContext.parseOrNull<UUID>(PRE_AUTH_TOKEN)
        ) doReturn preAuthToken

        val result = assertDoesNotThrow {
            memberInfo.preAuthToken
        }

        assertThat(result).isEqualTo(preAuthToken)
    }

    @Test
    fun `Pre-auth token from member context is null if not set`() {
        whenever(
            memberContext.parseOrNull<UUID>(PRE_AUTH_TOKEN)
        ) doReturn null

        val result = assertDoesNotThrow {
            memberInfo.preAuthToken
        }

        assertThat(result).isNull()
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
                ).entries)

            val result = assertDoesNotThrow { memberInfo.isNotary() }

            assertThat(result).isTrue
        }
    }
}