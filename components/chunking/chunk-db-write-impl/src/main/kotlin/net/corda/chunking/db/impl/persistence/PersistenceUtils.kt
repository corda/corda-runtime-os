package net.corda.chunking.db.impl.persistence

import net.corda.libs.packaging.core.CpiIdentifier

object PersistenceUtils {

    val CpiIdentifier.signerSummaryHashForDbQuery: String
        get() {
            return signerSummaryHash.toString()
        }
}