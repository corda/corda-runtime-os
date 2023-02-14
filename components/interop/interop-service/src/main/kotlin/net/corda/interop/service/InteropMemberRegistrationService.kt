package net.corda.interop.service

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.membership.lib.MemberInfoExtension
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
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component

@Component(service = [InteropMemberRegistrationService::class])
class InteropMemberRegistrationService {

    companion object {
        private val ALICE_ALTER_EGO_X500 = "CN=Alice Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB"
        private val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        private val ALICE_ALTER_EGO_X500_NAME = MemberX500Name.parse(ALICE_ALTER_EGO_X500)
        private val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")!!.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")!!.readText()
    }

    //Below method is to push the dummy interops memeber data to MEMBER_LIST_TOPIC
     fun createDummyMemberInfo(): List<Record<String, PersistentMemberInfo>> {
        val memberList = listOf(HoldingIdentity(ALICE_X500_NAME, "group1"), HoldingIdentity(ALICE_ALTER_EGO_X500_NAME, "group1"))
        val registeringMember = memberList[0]
        val memberId = registeringMember.shortHash.value

        val memberContext = listOf(
            KeyValuePair(PARTY_NAME, "PARTY_NAME"),
            KeyValuePair(PARTY_SESSION_KEY, "PARTY_SESSION_KEY"),
            KeyValuePair(SESSION_KEY_HASH, "SESSION_KEY_HASH"),
            KeyValuePair(GROUP_ID, "GROUP_ID"),
            KeyValuePair(LEDGER_KEYS_KEY.format(0), "LEDGER_KEYS_KEY"),
            KeyValuePair(LEDGER_KEY_HASHES_KEY.format(0), "LEDGER_KEY_HASHES_KEY"),
            KeyValuePair(LEDGER_KEY_HASHES_KEY.format(0), "LEDGER_KEY_HASHES_KEY"),
            KeyValuePair(SOFTWARE_VERSION, "SV-5.0"),
            KeyValuePair(PLATFORM_VERSION, "PV-5.0"),
            KeyValuePair(MEMBER_CPI_NAME, "MEMBER_CPI_NAME"),
            KeyValuePair(MEMBER_CPI_VERSION, "CPIV-1.0"),
            KeyValuePair(MEMBER_CPI_SIGNER_HASH, "CPIV-1.0")
        )
        val mgmContext = listOf(
            KeyValuePair(STATUS, MemberInfoExtension.MEMBER_STATUS_ACTIVE),
            KeyValuePair(MODIFIED_TIME, UTCClock().instant().toString()),
            KeyValuePair(PARTY_NAME, "1"),
        )
        return memberList.map {
            Record(
                Schemas.Membership.MEMBER_LIST_TOPIC,
                "${it.shortHash}-$memberId",
                PersistentMemberInfo(
                    registeringMember.toAvro(),
                    KeyValuePairList(memberContext),
                    KeyValuePairList(mgmContext)
                )
            )
        }
    }

    fun createDummyHostedIdentity(): Record<String, HostedIdentityEntry> {
        val registeringMember = HoldingIdentity(ALICE_X500_NAME, "group1")
        val hostedIdentity = HostedIdentityEntry(
            net.corda.data.identity.HoldingIdentity(registeringMember.x500Name.toString(), registeringMember.groupId),
            registeringMember.shortHash.value,
            registeringMember.shortHash.value,
            listOf(DUMMY_CERTIFICATE),
            DUMMY_PUBLIC_SESSION_KEY,
            null
        )
        return Record(
            Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
            registeringMember.shortHash.value,
            hostedIdentity
        )
    }
}