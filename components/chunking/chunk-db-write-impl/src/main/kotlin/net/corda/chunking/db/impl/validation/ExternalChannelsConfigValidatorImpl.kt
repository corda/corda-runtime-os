package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.core.CpkIdentifier
import org.slf4j.LoggerFactory

class ExternalChannelsConfigValidatorImpl: ExternalChannelsConfigValidator {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun validate(cpkIdentifier: CpkIdentifier, externalChannelsConfig: String?) {
        if (externalChannelsConfig==null) {
            log.debug("Skipping null external channel configuration string for $cpkIdentifier")
            return
        }

        throw NotImplementedError("Failed to validate configuration. Method not implemented")
    }
}
