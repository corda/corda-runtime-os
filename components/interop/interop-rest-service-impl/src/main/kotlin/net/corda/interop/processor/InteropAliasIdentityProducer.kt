package net.corda.interop.processor

import net.corda.data.interop.InteropAliasIdentity
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class InteropAliasIdentityProducer(
    private val publisher: Publisher?
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val INTEROP_ALIAS_IDENTITY_TOPIC = "interop.alias.identity"
    }

    fun publishAliasIdentity(shortHash: String, interopAliasIdentity: InteropAliasIdentity) {
        publisher?.publish(listOf(Record(INTEROP_ALIAS_IDENTITY_TOPIC, shortHash, interopAliasIdentity)))
        logger.info("Interop alias identity published with key : $shortHash and value : $interopAliasIdentity")
    }
}