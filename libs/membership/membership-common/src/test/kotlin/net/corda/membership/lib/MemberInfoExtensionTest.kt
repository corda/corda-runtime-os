package net.corda.membership.lib

import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.cpiInfo
import net.corda.membership.lib.MemberInfoExtension.Companion.softwareVersion
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MemberInfoExtensionTest {
    private val memberContext: MemberContext = mock()
    private val memberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberContext
    }

    @Test
    fun `CPI Info can be parsed from member context`() {
        val cpiName = "cpi"
        val cpiVersion = "1.3"
        val cpiSignerHash = "ALG:A1B2C3D4"
        whenever(
            memberContext.parse(
                eq(MEMBER_CPI_NAME),
                eq(String::class.java)
            )
        ) doReturn cpiName

        whenever(
            memberContext.parse(
                eq(MEMBER_CPI_VERSION),
                eq(String::class.java)
            )
        ) doReturn cpiVersion

        whenever(
            memberContext.parse(
                eq(MEMBER_CPI_SIGNER_HASH),
                eq(String::class.java)
            )
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
            memberContext.parse(
                eq(SOFTWARE_VERSION),
                eq(String::class.java)
            )
        ) doReturn softwareVersion

        val result = assertDoesNotThrow {
            memberInfo.softwareVersion
        }

        assertThat(result).isEqualTo(softwareVersion)
    }
}