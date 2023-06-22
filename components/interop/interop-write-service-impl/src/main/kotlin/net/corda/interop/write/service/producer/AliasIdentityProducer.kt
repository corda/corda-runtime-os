package net.corda.interop.write.service.producer

import net.corda.data.interop.InteropAliasIdentity
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.INTEROP_ALIAS_IDENTITY_TOPIC
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class AliasIdentityProducer(
    private val publisher: AtomicReference<Publisher?>
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun publishAliasIdentity(shortHash: String, interopAliasIdentity: InteropAliasIdentity) {
        if (publisher.get() == null) {
            logger.error("Interop alias identity publisher is null, not publishing.")
            return
        }

        val futures = publisher.get()!!.publish(listOf(Record(INTEROP_ALIAS_IDENTITY_TOPIC, shortHash, interopAliasIdentity)))
        futures.forEach { it.get() }
        logger.info("Interop alias identity published with key : $shortHash and value : $interopAliasIdentity")
    }
}
