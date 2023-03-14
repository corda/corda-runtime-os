package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class ExternalChannelsConfigValidatorImpl: ExternalChannelsConfigValidator {

    override fun validate(cpi: Cpi) {
        cpi.metadata.cpksMetadata.forEach {
            validate(it.externalChannelsConfig)
        }
    }

    private fun validate(externalChannelsConfig: String?) {
        if(externalChannelsConfig!=null){
            throw SchemaValidationError("The external channels configuration '$externalChannelsConfig' is invalid")
        }
    }
}

class SchemaValidationError(message: String): Exception(message)
