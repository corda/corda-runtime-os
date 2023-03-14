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

    private fun validate(externalChannelsConfig: String?) {
        if(externalChannelsConfig!=null){
            throw SchemaValidationError("The external channels configuration '$externalChannelsConfig' is invalid")
        }
    }
}
