package net.corda.interop.service.impl

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.interop.InteropMessage
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.interop.service.InteropMemberRegistrationService
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Suppress("unused")
@Component(service = [InteropMemberRegistrationService::class])
class HardcodedInteropMemberRegistrationService @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
): InteropMemberRegistrationService {

    companion object {
        private const val ALICE_ALTER_EGO_X500 = "CN=Alice Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB"
        private const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        private val ALICE_ALTER_EGO_X500_NAME = MemberX500Name.parse(ALICE_ALTER_EGO_X500)
        private val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
        private const val INTEROP_GROUP_ID = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")?.readText()
        private val memberList =
            listOf(HoldingIdentity(ALICE_X500_NAME, INTEROP_GROUP_ID), HoldingIdentity(ALICE_ALTER_EGO_X500_NAME, INTEROP_GROUP_ID))
    }

    //Below method is to push the dummy interops member data to MEMBER_LIST_TOPIC
    override fun createDummyMemberInfo(): List<Record<String, PersistentMemberInfo>> {
        val memberInfoList = mutableListOf<Record<String, PersistentMemberInfo>>()
        memberList.forEach {member ->
            val memberContext = listOf(
                KeyValuePair(PARTY_NAME, member.x500Name.toString()),
                KeyValuePair(String.format(URL_KEY, "0"), "http://localhost:8080"),
                KeyValuePair(String.format(PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(PARTY_SESSION_KEY, DUMMY_CERTIFICATE),
                KeyValuePair(SESSION_KEY_HASH, "9DEA9C982267BD142162ADC141C1C11C2F547C3C37B4C693A3EA3A017C2C6563"),
                KeyValuePair(GROUP_ID, INTEROP_GROUP_ID),
                KeyValuePair(LEDGER_KEYS_KEY.format(0), DUMMY_PUBLIC_SESSION_KEY),
                KeyValuePair(
                    LEDGER_KEY_HASHES_KEY.format(0),
                    "DFE65EAD29C556DF3A9C94C1A0F2C2155FFCC0768A282E18985BB021E8103B9D"
                ),
                KeyValuePair(LEDGER_KEY_SIGNATURE_SPEC.format(0), "SHA256withECDSA"),
                KeyValuePair(SOFTWARE_VERSION, "5.0.0.0-Fox10-RC03"),
                KeyValuePair(PLATFORM_VERSION, "5000"),
                //TODO : Following info may not be required for interops group,
                // need to investigate that LinkManager is happy without this info.
//            KeyValuePair(MEMBER_CPI_NAME, "calculator.cpi"),
//            KeyValuePair(MEMBER_CPI_VERSION, "1.0.0.0-SNAPSHOT"),
//            KeyValuePair(
//                MEMBER_CPI_SIGNER_HASH,
//                "SHA-256:367DDC08BB0BFBC8B338E2B8DC17EB1715A542386E6FE2376A9FB9EBC80A3DEC"
//            )
            ).sorted()
            val mgmContext = listOf(
                KeyValuePair(STATUS, "ACTIVE"),
                KeyValuePair(MODIFIED_TIME, Instant.now().toString()),
                KeyValuePair(MemberInfoExtension.SERIAL, "1"),
            ).sorted()
            memberInfoList.addAll(memberList.map {
                Record(
                    Schemas.Membership.MEMBER_LIST_TOPIC,
                    "${it.shortHash}-${member.shortHash.value}",
                    PersistentMemberInfo(
                        it.toAvro(),
                        KeyValuePairList(memberContext),
                        KeyValuePairList(mgmContext)
                    )
                )
            })
        }
        return memberInfoList
    }

    override fun createDummyHostedIdentity(): List<Record<String, HostedIdentityEntry>> {
        return memberList.map {
            val registeringMember = it
            val hostedIdentity = HostedIdentityEntry(
                net.corda.data.identity.HoldingIdentity(
                    registeringMember.x500Name.toString(),
                    registeringMember.groupId
                ),
                registeringMember.shortHash.value,
                listOf(DUMMY_CERTIFICATE),
                DUMMY_PUBLIC_SESSION_KEY,
                null
            )
            Record(
                Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC,
                registeringMember.shortHash.value,
                hostedIdentity
            )
        }
    }

    override fun seedMessage() : List<Record<*,*>> {
        val interopMessageSerializer = cordaAvroSerializationFactory.createAvroSerializer<InteropMessage> { }
        val key = "seed-message-1"
        val header = UnauthenticatedMessageHeader(memberList.first().toAvro(), memberList[1].toAvro(), "interop", "1")
        val payload = """
            {
                "method": "org.corda.interop/platform/tokens/v1.0/reserve-tokens",
                "parameters" : [ { "abc" : { "type" : "string", "value" : "USD" } } ] 
            }
        """.trimIndent()

        val interopMessage = InteropMessage("1", payload)

        return listOf(Record(
            Schemas.P2P.P2P_IN_TOPIC, key,
            AppMessage(UnauthenticatedMessage(header, ByteBuffer.wrap(interopMessageSerializer.serialize(interopMessage))))))
    }
}