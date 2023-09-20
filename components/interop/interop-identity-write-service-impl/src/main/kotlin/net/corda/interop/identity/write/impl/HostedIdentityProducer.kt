package net.corda.interop.identity.write.impl

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.ShortHash
import java.util.concurrent.ExecutionException
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
class HostedIdentityProducer(
    private val publisher: AtomicReference<Publisher?>,
    private val cryptoOpsClient: CryptoOpsClient
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val DUMMY_CERTIFICATE = this::class.java.getResource("/dummy_certificate.pem")?.readText()
        private val DUMMY_PUBLIC_SESSION_KEY = this::class.java.getResource("/dummy_session_key.pem")?.readText()
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

    private fun createHostedIdentityRecord(vNodeShortHash: ShortHash, interopIdentity: InteropIdentity): Record<String, HostedIdentityEntry> {
        val interopIdentityShortHash = computeShortHash(interopIdentity.x500Name, interopIdentity.groupId)
        val sessionKey = cryptoOpsClient.generateKeyPair(
            tenantId = vNodeShortHash.value,
            category = "SESSION_INIT",
            alias = interopIdentity.x500Name,
            scheme = "CORDA.ECDSA.SECP256R1",
        )
        logger.info("**********************************************")
        logger.info("************************* sessionKey : ${sessionKey}")
        logger.info("**********************************************")
        val hostedIdentity = HostedIdentityEntry(
            HoldingIdentity(interopIdentity.x500Name, interopIdentity.groupId),
            interopIdentityShortHash.toString(),
            //TODO CORE-15168
            emptyList(),
            HostedIdentitySessionKeyAndCert(sessionKey.toString(), null),
            emptyList()
        )

        return Record(P2P_HOSTED_IDENTITIES_TOPIC, interopIdentityShortHash.toString(), hostedIdentity)
    }
}
