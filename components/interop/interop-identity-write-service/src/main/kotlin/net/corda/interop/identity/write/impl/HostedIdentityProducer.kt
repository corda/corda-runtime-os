package net.corda.interop.identity.write.impl

import java.util.concurrent.ExecutionException
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import net.corda.crypto.core.ShortHash
import net.corda.data.identity.HoldingIdentity
import net.corda.interop.core.InteropIdentity


/**
 * Producer for publishing interop identities onto the hosted identities topic
 */
class HostedIdentityProducer(private val publisher: AtomicReference<Publisher?>) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val DUMMY_CERTIFICATE = this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY = this::class.java.getResource("/dummy_session_key.pem")?.readText()
    }

    fun clearHostedInteropIdentity(interopIdentityShortHash: ShortHash) {
        val pub = publisher.get()

        if (pub == null) {
            logger.error("Interop hosted identity publisher is null, not clearing.")
            return
        }

        val record = createHostedIdentityRecord(interopIdentityShortHash, null)

        val futures = pub.publish(listOf(record))

        try {
            futures.single().get()
        } catch (e: ExecutionException) {
            logger.error("Failed to clear interop identity from hosted identities topic.", e)
            return
        }

        logger.info("Interop hosted identity with key : ${record.key} cleared from hosted identities topic.")
    }

    fun publishHostedInteropIdentity(identity: InteropIdentity) {
        val pub = publisher.get()

        if (pub == null) {
            logger.error("Interop hosted identity publisher is null, not publishing.")
            return
        }

        val record = createHostedIdentityRecord(identity.shortHash, identity)

        val futures = pub.publish(listOf(record))

        try {
            futures.single().get()
        } catch (e: ExecutionException) {
            logger.error("Failed to publish interop identity to hosted identities topic.", e)
            return
        }

        logger.info("Interop hosted identity published with key : ${record.key} and value : ${record.value}")
    }

    private fun createHostedIdentityRecord(key: ShortHash, identity: InteropIdentity?): Record<String, HostedIdentityEntry> {
        val hostedIdentity = identity?.let {
            HostedIdentityEntry(
                HoldingIdentity(it.x500Name, it.groupId.toString()),
                key.toString(),
                //TODO CORE-15168
                emptyList(),
                HostedIdentitySessionKeyAndCert(DUMMY_PUBLIC_SESSION_KEY, null),
                emptyList()
            )
        }

        return Record(P2P_HOSTED_IDENTITIES_TOPIC, key.toString(), hostedIdentity)
    }
}
