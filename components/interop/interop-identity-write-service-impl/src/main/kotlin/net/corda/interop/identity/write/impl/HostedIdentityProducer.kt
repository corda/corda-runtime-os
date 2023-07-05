package net.corda.interop.identity.write.impl

import net.corda.data.interop.InteropIdentity
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference


/**
 * Producer for publishing interop identities onto the hosted identities topic
 */
class HostedIdentityProducer(private val publisher: AtomicReference<Publisher?>) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val DUMMY_CERTIFICATE = this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY = this::class.java.getResource("/dummy_session_key.pem")?.readText()
    }

    fun publishHostedInteropIdentity(identity: InteropIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop hosted identity publisher is null, not publishing.")
            return
        }

        val record = createHostedIdentityRecord(identity)

        publisher.get()!!.publish(listOf(record))
        logger.info("Interop hosted identity published with key : ${record.key} and value : ${record.value}")
    }

    private fun createHostedIdentityRecord(identity: InteropIdentity): Record<String, HostedIdentityEntry> {
        val shortHash = computeShortHash(identity.x500Name, identity.groupId)

        val hostedIdentity = HostedIdentityEntry(
            net.corda.data.identity.HoldingIdentity(identity.x500Name, identity.groupId),
            shortHash,
            //TODO CORE-15168
            listOf(DUMMY_CERTIFICATE),
            HostedIdentitySessionKeyAndCert(DUMMY_PUBLIC_SESSION_KEY, null),
            emptyList()
        )

        return Record(P2P_HOSTED_IDENTITIES_TOPIC, shortHash, hostedIdentity)
    }
}
