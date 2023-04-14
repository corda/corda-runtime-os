package net.corda.interop

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.membership.lib.MemberInfoExtension
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class InteropAliasProcessor(
    private val publisher: Publisher,
    private val aliases: MutableList<Pair<HoldingIdentity, HoldingIdentity>> = mutableListOf()
) : CompactedProcessor<String, HostedIdentityEntry> {
    override val keyClass = String::class.java
    override val valueClass = HostedIdentityEntry::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        @JvmStatic
        var identityMappingCache = ConcurrentHashMap<String, HoldingIdentity>()

        fun getRealHoldingIdentity(recipientId: String?): HoldingIdentity? {
            return identityMappingCache[recipientId]
        }

        private const val POSTFIX_DENOTING_ALIAS = " Alias"

        private val DUMMY_CERTIFICATE =
            this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY =
            this::class.java.getResource("/dummy_session_key.pem")?.readText()

        fun addAliasSubstringToOrganisationName(holdIdentity: HoldingIdentity): HoldingIdentity {
            val oldName = holdIdentity.x500Name
            val (commonName, organization) = if (oldName.commonName != null)
                Pair(oldName.commonName + POSTFIX_DENOTING_ALIAS, oldName.organization)
            else
                Pair(oldName.commonName, oldName.organization + POSTFIX_DENOTING_ALIAS)
            val newName = MemberX500Name(
                commonName,
                oldName.organizationUnit,
                organization,
                oldName.locality,
                oldName.state, oldName.country
            )
            return holdIdentity.copy(x500Name = newName)
        }

        fun createAliasMemberInfo(alias: HoldingIdentity, real: HoldingIdentity, group: List<HoldingIdentity>):
                List<Record<String, PersistentMemberInfo>> {
            val groupId = InteropProcessor.INTEROP_GROUP_ID
            val memberContext = listOf(
                KeyValuePair(MemberInfoExtension.PARTY_NAME, alias.x500Name.toString()),
                KeyValuePair(String.format(MemberInfoExtension.URL_KEY, "0"), "http://localhost:8080"),
                KeyValuePair(String.format(MemberInfoExtension.PROTOCOL_VERSION, "0"), "1"),
                KeyValuePair(String.format(MemberInfoExtension.PARTY_SESSION_KEYS, 0), DUMMY_CERTIFICATE),
                KeyValuePair(
                    MemberInfoExtension.SESSION_KEYS_HASH.format(0),
                    "9DEA9C982267BD142162ADC141C1C11C2F547C3C37B4C693A3EA3A017C2C6563"
                ),
                KeyValuePair(MemberInfoExtension.GROUP_ID, groupId),
                KeyValuePair(MemberInfoExtension.LEDGER_KEYS_KEY.format(0), DUMMY_PUBLIC_SESSION_KEY),
                KeyValuePair(
                    MemberInfoExtension.LEDGER_KEY_HASHES_KEY.format(0),
                    "DFE65EAD29C556DF3A9C94C1A0F2C2155FFCC0768A282E18985BB021E8103B9D"
                ),
                KeyValuePair(MemberInfoExtension.LEDGER_KEY_SIGNATURE_SPEC.format(0), "SHA256withECDSA"),
                KeyValuePair(MemberInfoExtension.SOFTWARE_VERSION, "5.0.0.0-Fox10-RC03"),
                KeyValuePair(MemberInfoExtension.PLATFORM_VERSION, "5000"),
                KeyValuePair(MemberInfoExtension.INTEROP_ROLE, "interop"),
                KeyValuePair("corda.interop.mapping.x500name", real.x500Name.toString()),
                KeyValuePair("corda.interop.mapping.group", real.groupId)
            ).sorted()
            val mgmContext = listOf(
                KeyValuePair(MemberInfoExtension.STATUS, "ACTIVE"),
                KeyValuePair(MemberInfoExtension.MODIFIED_TIME, Instant.now().toString()),
                KeyValuePair(MemberInfoExtension.SERIAL, "1"),
            ).sorted()
            return group.map { viewOwningMember ->
                Record(
                    Schemas.Membership.MEMBER_LIST_TOPIC,
                    "${viewOwningMember.shortHash}-${alias.shortHash.value}",
                    PersistentMemberInfo(
                        viewOwningMember.copy(groupId = InteropProcessor.INTEROP_GROUP_ID).toAvro(),
                        KeyValuePairList(memberContext),
                        KeyValuePairList(mgmContext)
                    )
                )
            }
        }

        fun createHostedAliasIdentity(holdingIdentity: HoldingIdentity): Record<String, HostedIdentityEntry> {
            val hostedIdentity = HostedIdentityEntry(
                net.corda.data.identity.HoldingIdentity(holdingIdentity.x500Name.toString(),
                    InteropProcessor.INTEROP_GROUP_ID
                ),
                holdingIdentity.shortHash.value,
                listOf(DUMMY_CERTIFICATE),
                HostedIdentitySessionKeyAndCert(DUMMY_PUBLIC_SESSION_KEY, null),
                emptyList()
            )
            return Record(Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC, holdingIdentity.shortHash.value, hostedIdentity)
        }
    }

    override fun onNext(
        newRecord: Record<String, HostedIdentityEntry>,
        oldValue: HostedIdentityEntry?,
        currentData: Map<String, HostedIdentityEntry>
    ) {
        logger.info("currentData=${currentData.size} newRecord=${newRecord}") //TODO remove once CORE-10444 is done

        if (oldValue != null) {
            identityMappingCache.remove(oldValue.holdingIdentity.x500Name.toString())
        }
        val newIdentity = newRecord.value
        if (newIdentity != null) {
            addEntry(newIdentity)
        }
    }

    override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
        logger.info("currentData=${currentData.size}") //TODO remove once CORE-10444 is done
        identityMappingCache.clear()
        currentData.values.forEach {
            addEntry(it)
        }
    }

    private fun addEntry(entry: HostedIdentityEntry) {
        val info = entry.holdingIdentity.toCorda()
        identityMappingCache[entry.holdingIdentity.x500Name.toString()] = info
        val newIdentity = entry
        val holdIdentity = newIdentity.holdingIdentity.toCorda()
        if (!holdIdentity.x500Name.organization.contains("Alias")) {
            val syntheticName = addAliasSubstringToOrganisationName(newIdentity.holdingIdentity.toCorda())
            val syntheticIdentities = listOf(createHostedAliasIdentity(syntheticName))
            logger.info("Adding hosted alias=$syntheticIdentities")
            publisher.publish(syntheticIdentities)
            aliases.add(Pair(syntheticName, holdIdentity))
            if (aliases.size >= 2) {
                val syntheticMemberInfos = aliases.flatMap { (alias, real) ->
                    createAliasMemberInfo(alias, real, aliases.map { it.first })
                }
                logger.info("Adding alias member=$syntheticMemberInfos")
                publisher.publish(syntheticMemberInfos)
            }
        }
    }
}