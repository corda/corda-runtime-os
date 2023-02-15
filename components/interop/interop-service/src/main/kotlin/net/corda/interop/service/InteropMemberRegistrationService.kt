package net.corda.interop.service

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_SIGNATURE_SPEC
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
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [InteropMemberRegistrationService::class])
class InteropMemberRegistrationService {

    companion object {
        private val ALICE_ALTER_EGO_X500 = "CN=Alice Alter Ego, O=Alice Alter Ego Corp, L=LDN, C=GB"
        private val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        private val ALICE_ALTER_EGO_X500_NAME = MemberX500Name.parse(ALICE_ALTER_EGO_X500)
        private val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")?.readText()
        private val memberList = listOf(HoldingIdentity(ALICE_X500_NAME, "group1"), HoldingIdentity(ALICE_ALTER_EGO_X500_NAME, "group1"))
    }

    //Below method is to push the dummy interops memeber data to MEMBER_LIST_TOPIC
     fun createDummyMemberInfo(): List<Record<String, PersistentMemberInfo>> {
        val registeringMember = memberList[0]
        val memberId = registeringMember.shortHash.value
        val memberContext = listOf(
            KeyValuePair(PARTY_NAME, ALICE_X500),
            KeyValuePair(PARTY_SESSION_KEY, "-----BEGIN PUBLIC KEY-----\\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJI8YBSEpJDs+4HLZVeObqHzDq+OR\\nDhhbypT2UUKZGNpG8StV8bQ8YSFHLsyosdULMNI0r68l3k2Q6pKmpu91Jg==\\n-----END PUBLIC KEY-----\\n"),
            KeyValuePair(SESSION_KEY_HASH, "9DEA9C982267BD142162ADC141C1C11C2F547C3C37B4C693A3EA3A017C2C6563"),
            KeyValuePair(GROUP_ID, "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"),
            KeyValuePair(LEDGER_KEYS_KEY.format(0), "-----BEGIN PUBLIC KEY-----\\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWWiM/LoGe1589/22j+IeoggGarAA\\n8ckK4e0gz23IlZNTfBDWHRthwth/I2PKqiHTrRHZh3L6k41Sb2A/1TjX5A==\\n-----END PUBLIC KEY-----\\n"),
            KeyValuePair(LEDGER_KEY_HASHES_KEY.format(0), "DFE65EAD29C556DF3A9C94C1A0F2C2155FFCC0768A282E18985BB021E8103B9D"),
            KeyValuePair(LEDGER_KEY_SIGNATURE_SPEC.format(0), "SHA256withECDSA"),
            KeyValuePair(SOFTWARE_VERSION, "5.0.0.0-Fox10-RC03"),
            KeyValuePair(PLATFORM_VERSION, "5000"),
            KeyValuePair(MEMBER_CPI_NAME, "calculator.cpi"),
            KeyValuePair(MEMBER_CPI_VERSION, "1.0.0.0-SNAPSHOT"),
            KeyValuePair(MEMBER_CPI_SIGNER_HASH, "SHA-256:367DDC08BB0BFBC8B338E2B8DC17EB1715A542386E6FE2376A9FB9EBC80A3DEC")
        )
        val mgmContext = listOf(
            KeyValuePair(STATUS, "ACTIVE"),
            KeyValuePair(MODIFIED_TIME, Instant.now().toString()),
            KeyValuePair(MemberInfoExtension.SERIAL, "1"),
        )
        return memberList.map {
            Record(
                Schemas.Membership.MEMBER_LIST_TOPIC,
                "${it.shortHash}-$memberId",
                PersistentMemberInfo(
                    it.toAvro(),
                    KeyValuePairList(memberContext),
                    KeyValuePairList(mgmContext)
                )
            )
        }
    }

    fun createDummyHostedIdentity(): List<Record<String, HostedIdentityEntry>> {
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
}