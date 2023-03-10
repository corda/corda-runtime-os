package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.core.CpkIdentifier

interface ExternalChannelsConfigValidator {
    fun validate(cpkIdentifier: CpkIdentifier, externalChannelsConfig: String?)
}
