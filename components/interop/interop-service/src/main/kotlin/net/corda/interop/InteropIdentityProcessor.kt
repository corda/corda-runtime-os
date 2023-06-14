package net.corda.interop

import net.corda.data.interop.InteropAliasIdentity
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory

class InteropIdentityProcessor() : CompactedProcessor<String, InteropAliasIdentity> {

    override val keyClass = String::class.java
    override val valueClass = InteropAliasIdentity::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
        private const val POSTFIX_DENOTING_ALIAS = " Alias"
        private const val INTEROP_RESPONDER_FLOW = "INTEROP_RESPONDER_FLOW"
    }

    override fun onNext(
        newRecord: Record<String, InteropAliasIdentity>,
        oldValue: InteropAliasIdentity?,
        currentData: Map<String, InteropAliasIdentity>
    ) {
        logger.info("********************************")
        logger.info("Message Received onNext : newRecord : $newRecord oldValue : $oldValue currentData : $currentData")
        logger.info("********************************")
    }

    override fun onSnapshot(currentData: Map<String, InteropAliasIdentity>) {
        logger.info("********************************")
        logger.info("Message Received onSnapshot : currentData : $currentData")
        logger.info("********************************")
    }
}
