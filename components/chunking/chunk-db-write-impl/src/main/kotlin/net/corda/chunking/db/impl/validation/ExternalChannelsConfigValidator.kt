package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.core.CpkMetadata

interface ExternalChannelsConfigValidator {
    fun validate(cpksMetadata: Collection<CpkMetadata>)
}
