package net.corda.interop.identity.write.impl

import net.corda.data.interop.PersistentInteropIdentity
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.INTEROP_IDENTITY_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import net.corda.interop.core.InteropIdentity


class InteropIdentityProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishInteropIdentity(holdingIdentityShortHash: String, identity: InteropIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop identity publisher is null, not publishing.")
            return
        }

        // Topic key is a combination of the holding and interop identity short hashes
        val interopIdentityShortHash = computeShortHash(identity.x500Name, identity.groupId)
        val key = "$holdingIdentityShortHash:$interopIdentityShortHash"

        val recordValue = PersistentInteropIdentity(
            identity.groupId,
            identity.x500Name,
            identity.holdingIdentityShortHash == holdingIdentityShortHash,
            identity.facadeIds,
            identity.applicationName,
            identity.endpointUrl,
            identity.endpointProtocol
        )

        val futures = publisher.get()!!.publish(listOf(Record(INTEROP_IDENTITY_TOPIC, key, recordValue)))

        futures.forEach { it.get() }

        logger.info("Interop identity published with key : $key and value : $identity")
    }
}
