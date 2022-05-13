package net.corda.chunking.db.impl.persistence

import net.corda.libs.packaging.Cpi

object PersistenceUtils {

    val Cpi.Identifier.signerSummaryHashForDbQuery: String
        get() {
            return signerSummaryHash?.toString() ?: ""
        }
}