package net.corda.membership.impl.registration.dynamic.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class MemberTypeCheckerTest {
    private val groupId = "GROUP_ID"
    private val knownMember = MemberX500Name.parse("C=GB, L=London, O=Alice")
    private val unknownMember = MemberX500Name.parse("C=GB, L=London, O=Bob")
    private val mgm = MemberX500Name.parse("C=GB, L=London, O=MGM")
    private val mgmContext = mock<MGMContext> {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn true
    }
    private val memberContext = mock<MGMContext> {
        on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn false
    }
    private val mgmInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn mgmContext
    }
    private val memberInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn memberContext
    }

    private val reader = mock<MembershipGroupReader> {
        on { lookup(unknownMember) } doReturn null
        on { lookup(mgm) } doReturn mgmInfo
        on { lookup(knownMember) } doReturn memberInfo
    }
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(any()) } doReturn reader
    }

    private val memberTypeChecker = MemberTypeChecker(membershipGroupReaderProvider)

    @Test
    fun `isMgm return false for unknown member`() {
        assertThat(
            memberTypeChecker.isMgm(
                HoldingIdentity(
                    unknownMember.toString(),
                    groupId
                )
            )
        ).isFalse
    }

    @Test
    fun `isMgm return true for MGM`() {
        assertThat(
            memberTypeChecker.isMgm(
                HoldingIdentity(
                    mgm.toString(),
                    groupId
                )
            )
        ).isTrue
    }

    @Test
    fun `isMgm return false for not MGM member`() {
        assertThat(
            memberTypeChecker.isMgm(
                HoldingIdentity(
                    knownMember.toString(),
                    groupId
                ).toCorda()
            )
        ).isFalse
    }

    @Test
    fun `getMgmMemberInfo return the mgmInfo for an MGM`() {
        assertThat(
            memberTypeChecker.getMgmMemberInfo(
                HoldingIdentity(
                    mgm.toString(),
                    groupId
                ).toCorda()
            )
        ).isEqualTo(mgmInfo)
    }
}
