package net.corda.interop.identity.write.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.interop.core.InteropIdentity
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference


/**
 * Producer for publishing interop identities onto the hosted identities topic
 */
class HostedIdentityProducer(
    private val publisher: AtomicReference<Publisher?>,
    private val sessionKeyGenerator: SessionKeyGenerator
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishHostedInteropIdentity(vNodeShortHash: ShortHash, identity: InteropIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop hosted identity publisher is null, not publishing.")
            return
        }
        val record = createHostedIdentityRecord(vNodeShortHash, identity)
        val futures = publisher.get()!!.publish(listOf(record))
        try {
            futures.single().get()
        } catch (e: ExecutionException) {
            logger.error("Failed to publish interop identity to hosted identities topic.", e)
        }
        logger.info("Interop hosted identity published with key : ${record.key} and value : ${record.value}")
    }

    private fun createHostedIdentityRecord(
        vNodeShortHash: ShortHash,
        interopIdentity: InteropIdentity
    ): Record<String, HostedIdentityEntry> {
        val interopIdentityShortHash = computeShortHash(interopIdentity.x500Name, interopIdentity.groupId)
        val sessionKey = sessionKeyGenerator.getOrCreateSessionKey(interopIdentity, vNodeShortHash.value)
        val keyAndCert = HostedIdentitySessionKeyAndCert.newBuilder()
            .setSessionPublicKey(sessionKey)
            .build()
        val hostedIdentity = HostedIdentityEntry(
            HoldingIdentity(interopIdentity.x500Name, interopIdentity.groupId),
            interopIdentityShortHash.toString(),
            //TODO CORE-15168
            emptyList(),
            keyAndCert,
            emptyList()
        )
        return Record(P2P_HOSTED_IDENTITIES_TOPIC, interopIdentityShortHash.toString(), hostedIdentity)
    }
}
