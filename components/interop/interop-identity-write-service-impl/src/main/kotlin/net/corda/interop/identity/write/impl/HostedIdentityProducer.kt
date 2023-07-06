package net.corda.interop.identity.write.impl

import net.corda.data.interop.PersistentInteropIdentity
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.HostedIdentitySessionKeyAndCert
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
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

    fun publishHostedInteropIdentity(identity: InteropIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop hosted identity publisher is null, not publishing.")
            return
        }

        val record = createHostedIdentityRecord(identity)

        publisher.get()!!.publish(listOf(record))
        logger.info("Interop hosted identity published with key : ${record.key} and value : ${record.value}")
    }

    private fun createHostedIdentityRecord(interopIdentity: InteropIdentity): Record<String, HostedIdentityEntry> {
        val persistentInteropIdentity = PersistentInteropIdentity(
            interopIdentity.groupId,
            interopIdentity.x500Name,
            interopIdentity.holdingIdentityShortHash
        )

        val interopIdentityShortHash = computeShortHash(interopIdentity.x500Name, interopIdentity.groupId)

        val hostedIdentity = HostedIdentityEntry(
            HoldingIdentity(persistentInteropIdentity.x500Name, persistentInteropIdentity.groupId),
            interopIdentityShortHash,
            //TODO CORE-15168
            listOf(DUMMY_CERTIFICATE),
            HostedIdentitySessionKeyAndCert(DUMMY_PUBLIC_SESSION_KEY, null),
            emptyList()
        )

        return Record(P2P_HOSTED_IDENTITIES_TOPIC, interopIdentityShortHash, hostedIdentity)
    }
}
