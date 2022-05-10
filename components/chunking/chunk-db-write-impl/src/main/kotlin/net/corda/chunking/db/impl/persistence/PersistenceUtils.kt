package net.corda.chunking.db.impl.persistence

import net.corda.packaging.Cpi

object PersistenceUtils {

    val Cpi.Identifier.signerSummaryHashForDbQuery: String
        get() {
            return signerSummaryHash?.toString() ?: ""
        }
}