package net.corda.chunking.db.impl.persistence

import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkIdentifier

object PersistenceUtils {

    val CpiIdentifier.signerSummaryHashForDbQuery: String
        get() {
            return signerSummaryHash?.toString() ?: ""
        }

    val CpkIdentifier.signerSummaryHashForDbQuery: String
        get() {
            return signerSummaryHash.toString()
        }

    fun CpkIdentifier.toCpkKey() = CpkKey(name, version, signerSummaryHashForDbQuery)
}