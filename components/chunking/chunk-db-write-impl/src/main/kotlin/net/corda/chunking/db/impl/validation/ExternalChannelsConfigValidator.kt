package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.Cpi

interface ExternalChannelsConfigValidator {
    fun validate(cpi: Cpi)
}
