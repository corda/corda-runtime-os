package net.corda.chunking.db.impl.persistence

import net.corda.chunking.RequestId
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
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
     * @param cpiFileChecksum the checksum of the CPI file
     * @param requestId the request id for the CPI that is being uploaded
     * @param groupId the group id from the group policy file
     * @param changelogsExtractedFromCpi the list of entities containing Liquibase scripts for all cpks of the given cpi
     */
    @Suppress("LongParameterList")
    fun persistMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLog>
    ): CpiMetadataEntity

    /**
     * When CPI has previously been saved, delete all the stale data and update in place.
     *
     * @param cpi a [Cpi] object
     * @param cpiFileName the original CPI file name
     * @param cpiFileChecksum the checksum of the CPI file
     * @param requestId the request id for the CPI that is being uploaded
     * @param groupId the group id from the group policy file
     * @param changelogsExtractedFromCpi the list of entities containing Liquibase scripts for all cpks of the given cpi
     */
    @Suppress("LongParameterList")
    fun updateMetadataAndCpks(
        cpi: Cpi,
        cpiFileName: String,
        cpiFileChecksum: SecureHash,
        requestId: RequestId,
        groupId: String,
        changelogsExtractedFromCpi: List<CpkDbChangeLog>
    ): CpiMetadataEntity

    /**
     *  Get the group id for a given CPI
     *
     *  @return null if not found
     */
    fun getGroupId(cpiName: String, cpiVersion: String, signerSummaryHash: String): String?

    /**
     * Can we insert (or update) this CPI into the database given its name and groupId?
     *
     * Entries are 'unique' on `(name, groupId)`.
     *
     * @param cpiName the name of the CPI
     * @param cpiSignerSummaryHash signer summary hash of the CPI
     * @param cpiVersion version of the CPI
     * @param groupId the MGM group id that we want to use for this CPI
     * @param forceUpload flag indicating if this is part of a force upload operation
     * @param requestId upload request ID
     */
    @Suppress("LongParameterList")
    fun validateCanUpsertCpi(
        cpiName: String,
        cpiSignerSummaryHash: String,
        cpiVersion: String,
        groupId: String,
        forceUpload: Boolean,
        requestId: String
    )
}
