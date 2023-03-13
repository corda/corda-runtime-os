package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class ExternalChannelsConfigValidatorImpl: ExternalChannelsConfigValidator {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun validate(cpi: Cpi) {
        cpi.metadata.cpksMetadata.forEach {
            validate(it.cpkId, it.externalChannelsConfig)
        }
    }

    private fun validate(cpkIdentifier: CpkIdentifier, externalChannelsConfig: String?) {
        if (externalChannelsConfig == null) {
            log.debug { "Skipping null external channel configuration string for $cpkIdentifier" }
            return
        }

        throw NotImplementedError("Failed to validate configuration. Method not implemented")
    }
}
