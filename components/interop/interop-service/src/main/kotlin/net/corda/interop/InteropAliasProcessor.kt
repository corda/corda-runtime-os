package net.corda.interop

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class InteropAliasProcessor(
    private val publisher: Publisher,
    private val interopMembersProducer: InteropMembersProducer,
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

        fun removeAliasSubstringFromOrganisationName(holdIdentity: HoldingIdentity): HoldingIdentity {
            val oldName = holdIdentity.x500Name
            val (commonName, organization) = if (oldName.commonName != null)
                Pair(oldName.commonName?.replace(POSTFIX_DENOTING_ALIAS, ""), oldName.organization)
            else
                Pair(oldName.commonName, oldName.organization.replace(POSTFIX_DENOTING_ALIAS, ""))
            val newName = MemberX500Name(
                commonName,
                oldName.organizationUnit,
                organization,
                oldName.locality,
                oldName.state, oldName.country
            )
            return holdIdentity.copy(x500Name = newName)
        }

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
    }

    override fun onNext(
        newRecord: Record<String, HostedIdentityEntry>,
        oldValue: HostedIdentityEntry?,
        currentData: Map<String, HostedIdentityEntry>
    ) {
        logger.info("currentData=${currentData.size} newRecord=${newRecord}")

        if (oldValue != null) {
            identityMappingCache.remove(oldValue.holdingIdentity.x500Name.toString())
        }
        val newIdentity = newRecord.value
        if (newIdentity != null) {
            addEntry(newIdentity)
        }
    }

    override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
        logger.info("currentData=${currentData.size}")
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
            val syntheticIdentities = listOf(interopMembersProducer.createHostedAliasIdentity(syntheticName))
            logger.info("Adding hosted alias=$syntheticIdentities")
            publisher.publish(syntheticIdentities)
            aliases.add(Pair(syntheticName, holdIdentity))
            if (aliases.size >= 2) {
                val syntheticMemberInfos = aliases.flatMap { (alias, real) ->
                    interopMembersProducer.createAliasMemberInfo(alias, real, aliases.map { it.first })
                }
                logger.info("Adding alias member=$syntheticMemberInfos")
                publisher.publish(syntheticMemberInfos)
            }
        }
    }
}