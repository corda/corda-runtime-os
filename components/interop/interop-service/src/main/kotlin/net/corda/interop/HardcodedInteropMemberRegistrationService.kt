package net.corda.interop

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.interop.InteropProcessor.Companion.INTEROP_GROUP_ID
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.INTEROP_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.toAvro
import java.time.Instant

class HardcodedInteropMemberRegistrationService  {

    companion object {
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")?.readText()
    }

    //Below method is to push the dummy interops member data to MEMBER_LIST_TOPIC
    fun createAliasMemberInfo(
        alias: net.corda.virtualnode.HoldingIdentity,
        real: net.corda.virtualnode.HoldingIdentity,
        group: List<net.corda.virtualnode.HoldingIdentity>
    ): List<Record<String, PersistentMemberInfo>> {
        val groupId = INTEROP_GROUP_ID
        val memberContext = listOf(
            KeyValuePair(PARTY_NAME, alias.x500Name.toString()),
            KeyValuePair(String.format(URL_KEY, "0"), "http://localhost:8080"),
            KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
            KeyValuePair(String.format(PARTY_SESSION_KEYS, 0), DUMMY_CERTIFICATE),
            KeyValuePair(
                MemberInfoExtension.SESSION_KEYS_HASH.format(0),
                "9DEA9C982267BD142162ADC141C1C11C2F547C3C37B4C693A3EA3A017C2C6563"
            ),
            KeyValuePair(GROUP_ID, groupId),
            KeyValuePair(LEDGER_KEYS_KEY.format(0), DUMMY_PUBLIC_SESSION_KEY),
            KeyValuePair(
                LEDGER_KEY_HASHES_KEY.format(0),
                "DFE65EAD29C556DF3A9C94C1A0F2C2155FFCC0768A282E18985BB021E8103B9D"
            ),
            KeyValuePair(LEDGER_KEY_SIGNATURE_SPEC.format(0), "SHA256withECDSA"),
            KeyValuePair(SOFTWARE_VERSION, "5.0.0.0-Fox10-RC03"),
            KeyValuePair(PLATFORM_VERSION, "5000"),
            KeyValuePair(INTEROP_ROLE, "interop"),
            KeyValuePair("corda.interop.mapping.x500name", real.x500Name.toString()),
            KeyValuePair("corda.interop.mapping.group", real.groupId)
        ).sorted()
        val mgmContext = listOf(
            KeyValuePair(STATUS, "ACTIVE"),
            KeyValuePair(MODIFIED_TIME, Instant.now().toString()),
            KeyValuePair(MemberInfoExtension.SERIAL, "1"),
        ).sorted()
        return group.map { viewOwningMember ->
            Record(
                Schemas.Membership.MEMBER_LIST_TOPIC,
                "${viewOwningMember.shortHash}-${alias.shortHash.value}",
                PersistentMemberInfo(
                    viewOwningMember.copy(groupId = INTEROP_GROUP_ID).toAvro(),
                    KeyValuePairList(memberContext),
                    KeyValuePairList(mgmContext)
                )
            )
        }
    }

    fun createHostedAliasIdentity(holdingIdentity: net.corda.virtualnode.HoldingIdentity): Record<String, HostedIdentityEntry> {
        val hostedIdentity = HostedIdentityEntry(
            net.corda.data.identity.HoldingIdentity(holdingIdentity.x500Name.toString(), INTEROP_GROUP_ID),
            holdingIdentity.shortHash.value,
            listOf(DUMMY_CERTIFICATE),
            HostedIdentitySessionKeyAndCert(DUMMY_PUBLIC_SESSION_KEY, null),
            emptyList()
        )
        return Record(Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC, holdingIdentity.shortHash.value, hostedIdentity)
    }
}