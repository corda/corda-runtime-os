package net.corda.interop.aliasinfo.processor

import net.corda.data.interop.InteropAliasIdentity
import net.corda.interop.aliasinfo.cache.AliasInfoCacheService
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.LoggerFactory
import java.util.*

class InteropIdentityProcessor(
    private val cacheService: AliasInfoCacheService
) : CompactedProcessor<String, InteropAliasIdentity> {

    override val keyClass = String::class.java
    override val valueClass = InteropAliasIdentity::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    private data class RecordKey(
        val keyText: String
    ) {
        val shortHash: String
        val groupId: UUID

        init {
            val tokens = keyText.split(":")

            if (tokens.size != 2) {
                throw CordaRuntimeException("Invalid record key '$keyText'. Expected key of the format <short hash>:<group UUID>")
            }

            shortHash = if (tokens[0].length == 12) {
                tokens[0]
            } else {
                throw CordaRuntimeException("Invalid record key '$keyText', expected string of length 12, got ${tokens[0].length}.")
            }

            groupId = try {
                UUID.fromString(tokens[1])
            } catch (e: Exception) {
                throw CordaRuntimeException("Invalid record key '$keyText', failed to parse UUID.", e)
            }
        }
    }

    private fun updateCacheEntry(key: RecordKey, oldValue: InteropAliasIdentity, newValue: InteropAliasIdentity) {
        val aliasIdentities = cacheService.getAliasIdentities(key.shortHash)

        // Both the record key and values contain the interop group ID. Might as well perform a sanity check.
        if (key.groupId.toString() != oldValue.groupId || key.groupId.toString() != newValue.groupId) {
            throw CordaRuntimeException("Failed to update cache entry, group ID in key does not match record values.")
        }

        val groupId = key.groupId.toString()

        // If the old record isn't in the cache or doesn't match the cache content, log the error and the over-write.
        if (groupId !in aliasIdentities.keys) {
            logger.warn("Update: Old record is not present in the cache. Ignoring old entry.")
        } else if (aliasIdentities[groupId] != oldValue) {
            logger.warn("Update: Old record value does not match current cache content. Overwriting.")
        }

        cacheService.putAliasIdentity(key.shortHash, newValue)
    }

    private fun insertCacheEntry(key: RecordKey, newValue: InteropAliasIdentity) {
        val aliasIdentities = cacheService.getAliasIdentities(key.shortHash)

        // Sanity check.
        if (key.groupId.toString() != newValue.groupId) {
            throw CordaRuntimeException("Failed to insert cache entry, group ID in key does not match record value.")
        }

        val groupId = key.groupId.toString()

        if (groupId in aliasIdentities.keys) {
            logger.warn("Insert: Cache entry already exists. Overwriting.")
        }

        cacheService.putAliasIdentity(key.shortHash, newValue)
    }

    private fun removeCacheEntry(key: RecordKey, oldValue: InteropAliasIdentity) {
        val aliasIdentities = cacheService.getAliasIdentities(key.shortHash)

        // Sanity check.
        if (key.groupId.toString() != oldValue.groupId) {
            throw CordaRuntimeException("Failed to remove cache entry, group ID in key does not match record value.")
        }

        val groupId = key.groupId.toString()

        if (groupId in aliasIdentities.keys) {
            cacheService.removeAliasIdentity(key.shortHash, oldValue)
        } else {
            logger.warn("Remove: No cache entry exists for the provided group ID. Ignoring.")
        }
    }

    override fun onNext(
        newRecord: Record<String, InteropAliasIdentity>,
        oldValue: InteropAliasIdentity?,
        currentData: Map<String, InteropAliasIdentity>
    ) {
        logger.info("Message Received onNext : newRecord : $newRecord oldValue : $oldValue currentData : $currentData")

        val key = RecordKey(newRecord.key)
        val newValue = newRecord.value

        if ((newValue != null) && (oldValue != null)) {
            updateCacheEntry(key, oldValue, newValue)
        }

        if (newValue != null && oldValue == null) {
            insertCacheEntry(key, newValue)
        }

        if (newValue == null && oldValue != null) {
            removeCacheEntry(key, oldValue)
        }

        if (newValue == null && oldValue == null) {
            logger.warn("Old and new record values are both null. Nothing to be done.")
        }

        logger.info("$currentData")
    }

    override fun onSnapshot(currentData: Map<String, InteropAliasIdentity>) {
        logger.info("Message Received onSnapshot : currentData : $currentData")
    }
}
