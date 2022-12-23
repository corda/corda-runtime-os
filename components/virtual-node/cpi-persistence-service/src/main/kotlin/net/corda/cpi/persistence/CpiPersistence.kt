package net.corda.cpi.persistence

import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkDbChangeLogEntity
import net.corda.libs.packaging.Cpi
import net.corda.lifecycle.Lifecycle
import net.corda.v5.crypto.SecureHash
import kotlin.jvm.Throws

interface CpiPersistence: Lifecycle {
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
        requestId: String,
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
        requestId: String,
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
     * Can we insert (or update) this CPI into the database given its name and groupId?
     *
     * Entries are 'unique' on `(name, groupId)`.
     *
     * @param cpiName the name of the CPI
     * @param cpiSignerSummaryHash signer summary hash of the CPI
     * @param cpiVersion version of the CPI
     * @param groupId the MGM group id that we want to use for this CPI
     * @param forceUpload flag indicating if this is part of a force upload operation
     */
    @Suppress("LongParameterList")
    @Throws(CpiPersistenceValidationException::class, CpiPersistenceDuplicateCpiException::class)
    fun validateCanUpsertCpi(
        cpiName: String,
        cpiSignerSummaryHash: String,
        cpiVersion: String,
        groupId: String,
        forceUpload: Boolean
    )
}
