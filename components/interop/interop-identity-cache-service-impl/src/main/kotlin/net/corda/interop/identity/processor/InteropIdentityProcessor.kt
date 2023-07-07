package net.corda.interop.identity.processor

import net.corda.data.interop.PersistentInteropIdentity
import net.corda.interop.core.InteropIdentity
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.interop.identity.cache.InteropIdentityCacheService
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory

class InteropIdentityProcessor(
    private val cacheService: InteropIdentityCacheService
) : CompactedProcessor<String, PersistentInteropIdentity> {

    override val keyClass = String::class.java
    override val valueClass = PersistentInteropIdentity::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private fun verifyShortHash(identity: InteropIdentity, expectedShortHash: String) {
        val shortHash = computeShortHash(identity.x500Name, identity.groupId)
        if (shortHash != expectedShortHash) {
            throw CordaRuntimeException(
                "Interop identity $identity short hash does not match record key! " +
                "Expected: $expectedShortHash, got: $shortHash.")
        }
    }

    private data class RecordKey(
        val keyText: String
    ) {
        val holdingIdentityShortHash: String
        val interopIdentityShortHash: String

        init {
            val tokens = keyText.split(":")

            if (tokens.size != 2) {
                throw CordaRuntimeException("Invalid record key '$keyText'. Expected key of the format <short hash>:<short hash>")
            }

            holdingIdentityShortHash = if (tokens[0].length == 12) {
                tokens[0]
            } else {
                throw CordaRuntimeException("Invalid record key '$keyText', expected string of length 12, got ${tokens[0].length}.")
            }

            interopIdentityShortHash = if (tokens[1].length == 12) {
                tokens[1]
            } else {
                throw CordaRuntimeException("Invalid record key '$keyText', expected string of length 12, got ${tokens[1].length}.")
            }
        }
    }

    private fun updateCacheEntry(key: RecordKey, oldEntry: InteropIdentity, newEntry: InteropIdentity) {
        val interopIdentities = cacheService.getInteropIdentities(key.holdingIdentityShortHash)

        // Short hash can be derived from x500 name and group ID. Might as well perform a quick sanity check!
        verifyShortHash(oldEntry, key.interopIdentityShortHash)
        verifyShortHash(newEntry, key.interopIdentityShortHash)

        // Remove the old entry from the cache or print a warning if not present
        if (interopIdentities.contains(oldEntry)) {
            cacheService.removeInteropIdentity(key.holdingIdentityShortHash, oldEntry)
        } else {
            logger.warn("Update: Old record is not present in the cache. Ignoring old entry.")
        }

        cacheService.putInteropIdentity(key.holdingIdentityShortHash, newEntry)
    }

    private fun insertCacheEntry(key: RecordKey, newEntry: InteropIdentity) {
        val interopIdentities = cacheService.getInteropIdentities(key.holdingIdentityShortHash)

        // Short hash can be derived from x500 name and group ID. Might as well perform a quick sanity check!
        verifyShortHash(newEntry, key.interopIdentityShortHash)

        // If the new value is already in the cache, log a warning.
        if (!interopIdentities.contains(newEntry)) {
            cacheService.putInteropIdentity(key.holdingIdentityShortHash, newEntry)
        } else {
            logger.warn("Insert: Cache entry already exists. Ignoring.")
            return
        }
    }

    private fun removeCacheEntry(key: RecordKey, oldEntry: InteropIdentity) {
        val interopIdentities = cacheService.getInteropIdentities(key.holdingIdentityShortHash)

        // Short hash can be derived from x500 name and group ID. Might as well perform a quick sanity check!
        verifyShortHash(oldEntry, key.interopIdentityShortHash)

        // Remove the entry if present, log if not present when expected.
        if (interopIdentities.contains(oldEntry)) {
            cacheService.removeInteropIdentity(key.holdingIdentityShortHash, oldEntry)
        } else {
            logger.warn("Remove: No cache entry exists for the provided group ID. Ignoring.")
        }
    }

    override fun onNext(
        newRecord: Record<String, PersistentInteropIdentity>,
        oldValue: PersistentInteropIdentity?,
        currentData: Map<String, PersistentInteropIdentity>
    ) {
        val key = RecordKey(newRecord.key)
        val newValue = newRecord.value

        logger.info("Message Received onNext; key: $key, newValue: $newValue, oldValue: $oldValue")

        val oldEntry = oldValue?.let { InteropIdentity.of(key.holdingIdentityShortHash, it) }
        val newEntry = newValue?.let { InteropIdentity.of(key.holdingIdentityShortHash, it) }

        if ((newEntry != null) && (oldEntry != null)) {
            updateCacheEntry(key, oldEntry, newEntry)
        }

        if (newEntry != null && oldEntry == null) {
            insertCacheEntry(key, newEntry)
        }

        if (newEntry == null && oldEntry != null) {
            removeCacheEntry(key, oldEntry)
        }

        if (newEntry == null && oldEntry == null) {
            logger.warn("Old and new record values are both null. Nothing to be done.")
        }
    }

    override fun onSnapshot(currentData: Map<String, PersistentInteropIdentity>) {
        logger.info("Message Received onSnapshot; loading ${currentData.size} entries.")

        currentData.entries.forEach { topicEntry ->
            val keyInfo = RecordKey(topicEntry.key)
            val cacheEntry = InteropIdentity.of(keyInfo.holdingIdentityShortHash, topicEntry.value)
            cacheService.putInteropIdentity(keyInfo.holdingIdentityShortHash, cacheEntry)
        }
    }
}
