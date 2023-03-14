package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpkMetadata

class ExternalChannelsConfigValidatorImpl: ExternalChannelsConfigValidator {

    override fun validate(cpksMetadata: Collection<CpkMetadata>) {
        cpksMetadata.forEach {
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
