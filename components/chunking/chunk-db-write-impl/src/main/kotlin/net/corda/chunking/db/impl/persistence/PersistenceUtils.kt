package net.corda.chunking.db.impl.persistence

import net.corda.packaging.CPI

object PersistenceUtils {

    val CPI.Identifier.signerSummaryHashForDbQuery: String
        get() {
            return signerSummaryHash?.toString() ?: ""
        }
}