package net.corda.membership.service.impl.actions

import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.SelfSignedMemberInfo
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.utilities.parse
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

internal fun createHoldingIdentity(name: String, group: String): HoldingIdentity {
    return createTestHoldingIdentity("C=GB,L=London,O=$name", group)
}

internal fun mockSignedMemberInfo(
    holdingIdentity: HoldingIdentity,
    memberSerial: Long,
    isMgm: Boolean = false,
    status: String = MemberInfoExtension.MEMBER_STATUS_ACTIVE,
    isNotary: Boolean = false,
): SelfSignedMemberInfo {
    val mgmContext = mockMgmContext(holdingIdentity, isMgm, status)
    val memberContext = mockMemberContext(holdingIdentity, isNotary)
    return mock {
        on { mgmProvidedContext } doReturn mgmContext
        on { memberProvidedContext } doReturn memberContext
        on { name } doReturn holdingIdentity.x500Name
        on { groupId } doReturn holdingIdentity.groupId
        on { serial } doReturn memberSerial
        on { isActive } doReturn (status == MemberInfoExtension.MEMBER_STATUS_ACTIVE)
        on { memberSignature } doReturn CryptoSignatureWithKey(
            ByteBuffer.wrap("pk-${holdingIdentity.x500Name}".toByteArray()),
            ByteBuffer.wrap("sig-${holdingIdentity.x500Name}".toByteArray()),
        )
        on { memberSignatureSpec } doReturn CryptoSignatureSpec(
            "dummy", null, null
        )
    }
}

internal fun mockMemberInfo(
    holdingIdentity: HoldingIdentity,
    memberSerial: Long,
    isMgm: Boolean = false,
    status: String = MemberInfoExtension.MEMBER_STATUS_ACTIVE,
    isNotary: Boolean = false,
): MemberInfo {
    val mgmContext = mockMgmContext(holdingIdentity, isMgm, status)
    val memberContext = mockMemberContext(holdingIdentity, isNotary)
    return mock {
        on { mgmProvidedContext } doReturn mgmContext
        on { memberProvidedContext } doReturn memberContext
        on { name } doReturn holdingIdentity.x500Name
        on { groupId } doReturn holdingIdentity.groupId
        on { serial } doReturn memberSerial
        on { isActive } doReturn (status == MemberInfoExtension.MEMBER_STATUS_ACTIVE)
    }
}

private fun mockMgmContext(
    holdingIdentity: HoldingIdentity,
    isMgm: Boolean,
    status: String,
) = mock<MGMContext> {
    on { parseOrNull(eq(IS_MGM), any<Class<Boolean>>()) } doReturn isMgm
    on { parse(eq(STATUS), any<Class<String>>()) } doReturn status
    on { entries } doReturn mapOf("mgm" to holdingIdentity.x500Name.toString()).entries
}

private fun mockMemberContext(
    holdingIdentity: HoldingIdentity,
    isNotary: Boolean,
) = mock<MemberContext> {
    on { parse(eq(MemberInfoExtension.GROUP_ID), any<Class<String>>()) } doReturn holdingIdentity.groupId
    if (isNotary) {
        on { entries } doReturn mapOf(
            "member" to holdingIdentity.x500Name.toString(),
            "${MemberInfoExtension.ROLES_PREFIX}.0" to "notary",
        ).entries
        val notaryDetails = MemberNotaryDetails(
            holdingIdentity.x500Name,
            "Notary Plugin A",
            listOf(1, 2),
            listOf(mock()),
            true
        )
        whenever(mock.parse<MemberNotaryDetails>("corda.notary")).thenReturn(notaryDetails)
    } else {
        on { entries } doReturn mapOf("member" to holdingIdentity.x500Name.toString()).entries
    }
}