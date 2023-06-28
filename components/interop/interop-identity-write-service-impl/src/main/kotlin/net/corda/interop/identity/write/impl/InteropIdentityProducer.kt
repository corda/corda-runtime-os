package net.corda.interop.identity.write.impl

import net.corda.data.interop.InteropAliasIdentity
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.INTEROP_ALIAS_IDENTITY_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference


class InteropIdentityProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishInteropIdentity(shortHash: String, identity: InteropAliasIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop identity publisher is null, not publishing.")
            return
        }

        // Key is a combination of holding identity short hash and interop group ID.
        val key = "$shortHash:${identity.groupId}"

        val futures = publisher.get()!!.publish(listOf(Record(INTEROP_ALIAS_IDENTITY_TOPIC, key, identity)))

        futures.forEach { it.get() }

        logger.info("Interop identity published with key : $key and value : $identity")
    }
}
