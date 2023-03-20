package net.corda.interop

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

//Based on FlowP2PFilter
@Suppress("LongParameterList", "Unused")
class InteropAliasProcessor : CompactedProcessor<String, HostedIdentityEntry> {
    override val keyClass = String::class.java
    override val valueClass = HostedIdentityEntry::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"

        @JvmStatic
        var identityMappingCache = ConcurrentHashMap<String, HoldingIdentity>()

        fun getRealHoldingIdentity(recipientId: String?): HoldingIdentity? {
            return identityMappingCache[recipientId]
        }
    }

    override fun onNext(
        newRecord: Record<String, HostedIdentityEntry>,
        oldValue: HostedIdentityEntry?,
        currentData: Map<String, HostedIdentityEntry>
    ) {
        if (oldValue != null) {
            identityMappingCache.remove(oldValue.holdingIdentity.x500Name.toString())
        }
        val newIdentity = newRecord.value
        if (newIdentity != null) {
            addEntry(newIdentity)
        }
    }

    override fun onSnapshot(currentData: Map<String, HostedIdentityEntry>) {
        identityMappingCache.clear()
        currentData.values.forEach {
            addEntry(it)
        }
    }

    private fun addEntry(entry: HostedIdentityEntry) {
        val info = entry.toHoldingIdentity()
        identityMappingCache[entry.holdingIdentity.x500Name.toString()] = info
    }

    private fun HostedIdentityEntry.toHoldingIdentity(): HoldingIdentity {
        return this.holdingIdentity.toCorda()
    }
}