package net.corda.interop.service

import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.toAvro
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_SIGNER_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_CPI_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.utilities.time.UTCClock
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Reference


@Suppress("LongParameterList")
@Component(service = [InteropMemberRegistrationService::class])
class InteropMemberRegistrationService @Activate constructor(
    @Reference(service = MemberInfoFactory::class)
    private val memberInfoFactory: MemberInfoFactory
)  {

    val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"
    val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
    val BOB_X500_NAME = MemberX500Name.parse(BOB_X500)
    val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
    val BOB_X500_HOLDING_IDENTITY = net.corda.data.identity.HoldingIdentity(BOB_X500, "group1")
    val ALICE_X500_HOLDING_IDENTITY = net.corda.data.identity.HoldingIdentity(ALICE_X500, "group1")

    /**
     * Parses the static member list template, creates the MemberInfo for the registering member and the records for the
     * kafka publisher.
     */
    @Suppress("ThrowsCount")
     fun createDummyMemberInfo(): List<Record<String, PersistentMemberInfo>> {
        val registeringMember = HoldingIdentity(ALICE_X500_NAME, "group1")
        val memberId = registeringMember.shortHash.value
        val optionalContext = mapOf(MEMBER_CPI_SIGNER_HASH to "MEMBER_CPI_SIGNER_HASH")

        @Suppress("SpreadOperator")
        val memberContext = mapOf(
            PARTY_NAME to "PARTY_NAME",
            PARTY_SESSION_KEY to "PARTY_SESSION_KEY",
            SESSION_KEY_HASH to "SESSION_KEY_HASH",
            GROUP_ID to "GROUP_ID",
            LEDGER_KEYS_KEY.format(0) to "LEDGER_KEYS_KEY",
            LEDGER_KEY_HASHES_KEY.format(0) to "LEDGER_KEY_HASHES_KEY",
//            *convertEndpoints(staticMemberInfo).toTypedArray(),
//            *roles.toMemberInfo(::configureNotaryKey).toTypedArray(),
            SOFTWARE_VERSION to "SV-5.0",
            PLATFORM_VERSION to "PV-5.0",
            MEMBER_CPI_NAME to "MEMBER_CPI_NAME",
            MEMBER_CPI_VERSION to "CPIV-1.0"
        ) + optionalContext

        val memberInfo = memberInfoFactory.create(
            memberContext.toSortedMap(),
            sortedMapOf(
                STATUS to "ACTIVE",
                MODIFIED_TIME to UTCClock().instant().toString(),
                SERIAL to "1",
            )
        )

        return listOf( Record(
            Schemas.Membership.MEMBER_LIST_TOPIC,
            "${registeringMember.shortHash}-$memberId",
            PersistentMemberInfo(
                registeringMember.toAvro(),
                memberInfo.memberProvidedContext.toAvro(),
                memberInfo.mgmProvidedContext.toAvro()
            )
        )
        )
    }
}