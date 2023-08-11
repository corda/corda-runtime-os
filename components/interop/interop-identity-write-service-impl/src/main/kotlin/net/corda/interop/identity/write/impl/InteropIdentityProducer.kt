package net.corda.interop.identity.write.impl

import net.corda.data.interop.PersistentInteropIdentity
import net.corda.interop.core.InteropIdentity
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.INTEROP_IDENTITY_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import net.corda.crypto.core.ShortHash


class InteropIdentityProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishInteropIdentity(holdingIdentityShortHash: ShortHash, identity: InteropIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop identity publisher is null, not publishing.")
            return
        }

        // Topic key is a combination of the holding and interop identity short hashes
        val interopIdentityShortHash = computeShortHash(identity.x500Name, identity.groupId)
        val key = "$holdingIdentityShortHash:$interopIdentityShortHash"

        val listOfFacades = mutableListOf<String>()
            for (facade in identity.facadeIds) {
                listOfFacades.add(facade.toString())
            }

        val isLocal = identity.owningVirtualNodeShortHash == holdingIdentityShortHash
        logger.debug("Publishing Interop Identity for $holdingIdentityShortHash. Is Local $isLocal")

        val recordValue = PersistentInteropIdentity(
            identity.groupId,
            identity.x500Name,
            isLocal,
            listOfFacades,
            identity.applicationName,
            identity.endpointUrl,
            identity.endpointProtocol
        )

        val futures = publisher.get()!!.publish(listOf(Record(INTEROP_IDENTITY_TOPIC, key, recordValue)))

        futures.forEach { it.get() }

        logger.info("Interop identity published with key : $key and value : $identity")
    }
}
