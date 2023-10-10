package net.corda.interop.identity.write.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.interop.PersistentInteropIdentity
import net.corda.interop.core.InteropIdentity
import net.corda.interop.core.Utils.Companion.computeShortHash
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.INTEROP_IDENTITY_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference


class InteropIdentityProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishInteropIdentity(holdingIdentityShortHash: ShortHash, identity: InteropIdentity) {
        val pub = publisher.get()

        if (pub == null) {
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

        val recordValue = PersistentInteropIdentity(
            identity.groupId.toString(),
            identity.x500Name,
            identity.owningVirtualNodeShortHash.value,
            listOfFacades,
            identity.applicationName,
            identity.endpointUrl,
            identity.endpointProtocol,
            identity.enabled
        )

        val futures = pub.publish(listOf(Record(INTEROP_IDENTITY_TOPIC, key, recordValue)))

        futures.forEach { it.get() }

        logger.info("Interop identity published with key : $key and value : $identity")
    }

    fun clearInteropIdentity(holdingIdentityShortHash: ShortHash, interopIdentityShortHash: ShortHash) {
        val pub = publisher.get()

        if (pub == null) {
            logger.error("Interop identity publisher is null, not publishing.")
            return
        }

        val key = "$holdingIdentityShortHash:$interopIdentityShortHash"

        val record = Record(
            INTEROP_IDENTITY_TOPIC,
            key,
            null
        )

        val futures = pub.publish(listOf(record))

        futures.forEach { it.get() }

        logger.info("Interop identity with key : $key cleared from interop identity topic.")
    }
}
