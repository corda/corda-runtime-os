package net.corda.interop.identity.write.impl

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.interop.core.InteropIdentity
import net.corda.membership.lib.MemberInfoExtension
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory


class MembershipInfoProducer(val publisher: AtomicReference<Publisher?>) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val INTEROP_GROUP_ID = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"

        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")?.readText()

        private fun createInteropIdentityMemberInfo(
            holdingIdentity: HoldingIdentity,
            viewOwningIdentity: InteropIdentity,
            groupIdentities: List<InteropIdentity>
        ): List<Record<String, PersistentMemberInfo>> {
            val memberContext = listOf(
                KeyValuePair(MemberInfoExtension.PARTY_NAME, viewOwningIdentity.x500Name),
                KeyValuePair(String.format(MemberInfoExtension.URL_KEY, "0"), "http://localhost:8080"),
                KeyValuePair(String.format(MemberInfoExtension.PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(String.format(MemberInfoExtension.PARTY_SESSION_KEYS, 0), DUMMY_CERTIFICATE),
                KeyValuePair(
                    MemberInfoExtension.SESSION_KEYS_HASH.format(0),
                    "9DEA9C982267BD142162ADC141C1C11C2F547C3C37B4C693A3EA3A017C2C6563"
                ),
                KeyValuePair(MemberInfoExtension.GROUP_ID, INTEROP_GROUP_ID),
                KeyValuePair(MemberInfoExtension.LEDGER_KEYS_KEY.format(0), DUMMY_PUBLIC_SESSION_KEY),
                KeyValuePair(
                    MemberInfoExtension.LEDGER_KEY_HASHES_KEY.format(0),
                    "DFE65EAD29C556DF3A9C94C1A0F2C2155FFCC0768A282E18985BB021E8103B9D"
                ),
                KeyValuePair(MemberInfoExtension.LEDGER_KEY_SIGNATURE_SPEC.format(0), "SHA256withECDSA"),
                KeyValuePair(MemberInfoExtension.SOFTWARE_VERSION, "5.0.0.0-Fox10-RC03"),
                KeyValuePair(MemberInfoExtension.PLATFORM_VERSION, "5000"),
                KeyValuePair(MemberInfoExtension.INTEROP_ROLE, "interop"),
                KeyValuePair("corda.interop.mapping.x500name", holdingIdentity.x500Name.toString()),
                KeyValuePair("corda.interop.mapping.group", holdingIdentity.groupId)
            ).sorted()

            val mgmContext = listOf(
                KeyValuePair(MemberInfoExtension.STATUS, "ACTIVE"),
                KeyValuePair(MemberInfoExtension.MODIFIED_TIME, Instant.now().toString()),
                KeyValuePair(MemberInfoExtension.SERIAL, "1"),
            ).sorted()

            val viewOwningMemberHoldingIdentity = HoldingIdentity(
                MemberX500Name.parse(viewOwningIdentity.x500Name), viewOwningIdentity.groupId)

            return groupIdentities.map { groupIdentity ->
                Record(
                    Schemas.Membership.MEMBER_LIST_TOPIC,
                    "${viewOwningIdentity.shortHash}-${groupIdentity.shortHash}",
                    PersistentMemberInfo(
                        viewOwningMemberHoldingIdentity.copy(groupId = INTEROP_GROUP_ID).toAvro(),
                        KeyValuePairList(memberContext),
                        KeyValuePairList(mgmContext)
                    )
                )
            }
        }
    }

    fun publishMemberInfo(
        holdingIdentity: HoldingIdentity,
        viewOwningIdentity: InteropIdentity,
        groupIdentities: List<InteropIdentity>
    ) {
        if (publisher.get() == null) {
            log.error("Member info publisher is null, not publishing.")
            return
        }

        val memberInfoList = createInteropIdentityMemberInfo(holdingIdentity, viewOwningIdentity, groupIdentities)

        publisher.get()!!.publish(memberInfoList)
    }
}
