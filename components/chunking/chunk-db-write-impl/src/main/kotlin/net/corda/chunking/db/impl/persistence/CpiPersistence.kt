package net.corda.chunking.db.impl.persistence

import net.corda.chunking.RequestId
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.packaging.Cpi
import net.corda.v5.crypto.SecureHash

interface CpiPersistence {
    /**
     * Check if we already have a cpk persisted with this checksum
     *
     * @return true if checksum exists in the persistence layer
     */
    fun cpkExists(cpkChecksum: SecureHash): Boolean

    /** Checks to see if the CPI exists in the database using the primary key
     *
     * @return true if CPI exists
     */
    fun cpiExists(cpiName: String, cpiVersion: String, signerSummaryHash: String): Boolean

    /** Persist the CPI metadata and the CPKs
     *
     * @param cpi a [Cpi] object
     * @param cpiFileName the original CPI file name
     * @param checksum the checksum of the CPI file
     * @param requestId the request id for the CPI that is being uploaded
     * @param groupId the group id from the group policy file
     * @param cpkDbChangeLogEntities the list of entities containing Liquibase scripts for all cpks of the given cpi
     */
    @Suppress("LongParameterList")
    fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity

    /**
     * When CPI has previously been saved, delete all the stale data and update in place.
     *
     * @param cpi a [Cpi] object
     * @param cpiFileName the original CPI file name
     * @param checksum the checksum of the CPI file
     * @param requestId the request id for the CPI that is being uploaded
     * @param groupId the group id from the group policy file
     * @param cpkDbChangeLogEntities the list of entities containing Liquibase scripts for all cpks of the given cpi
     */
    @Suppress("LongParameterList")
    fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        checksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        cpkDbChangeLogEntities: List<CpkDbChangeLogEntity>
    ): CpiMetadataEntity

    /**
     *  Get the group id for a given CPI
     *
     *  @return null if not found
     */
    fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String?

    /**
     * Can we insert (or update) this Cpi into the database given its name and groupId?
     *
     * Entries are 'unique' on `(name, groupId)`.
     *
     * @param cpiName the name of the cpi
     * @param groupId the MGM group id that we want to use for this CPI
     *
     * @return
     *
     * false: If `(aaa, 123456)` is in the db, we cannot upsert `(bbb, 123456)` for
     * the same group id
     *
     * true: If `(aaa, 123456)` is in the db, we can still "upsert" `(aaa, 123456)`
     *
     * true: If `(aaa, 123456)` is in the db, we can upsert a new cpi `(bbb, 654321)`
     *
     * true: If nothing is in the db we can clearly insert `(any, any)`
     */
    fun canUpsertCpi(cpiName: String, groupId: String, forceUpload: Boolean = false, cpiVersion: String? = null): Boolean
}
