package net.corda.membership.impl.registration.dynamic.handler

import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.base.util.parse
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object TestUtils {
    private const val GROUP_ID = "group"

    internal fun mockMemberInfo(
        holdingIdentity: HoldingIdentity,
        isMgm: Boolean = false,
        status: String = MemberInfoExtension.MEMBER_STATUS_ACTIVE,
        isNotary: Boolean = false,
    ): MemberInfo {
        val mgmContext = mock<MGMContext> {
            on { parseOrNull(eq(MemberInfoExtension.IS_MGM), any<Class<Boolean>>()) } doReturn isMgm
            on { parse(eq(MemberInfoExtension.STATUS), any<Class<String>>()) } doReturn status
            on { entries } doReturn mapOf("mgm" to holdingIdentity.x500Name.toString()).entries
        }
        val memberContext = mock<MemberContext> {
            on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn holdingIdentity.groupId
            if (isNotary) {
                on { entries } doReturn mapOf(
                    "member" to holdingIdentity.x500Name.toString(),
                    "${MemberInfoExtension.ROLES_PREFIX}.0" to "notary",
                ).entries
                val notaryDetails = MemberNotaryDetails(
                    holdingIdentity.x500Name,
                    "Notary Plugin A",
                    listOf(mock())
                )
                whenever(mock.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
            } else {
                on { entries } doReturn mapOf("member" to holdingIdentity.x500Name.toString()).entries
            }
        }
        return mock {
            on { mgmProvidedContext } doReturn mgmContext
            on { memberProvidedContext } doReturn memberContext
            on { name } doReturn holdingIdentity.x500Name
            on { groupId } doReturn holdingIdentity.groupId
        }
    }

    internal fun createHoldingIdentity(name: String): HoldingIdentity {
        return createTestHoldingIdentity("C=GB,L=London,O=$name", GROUP_ID)
    }
}