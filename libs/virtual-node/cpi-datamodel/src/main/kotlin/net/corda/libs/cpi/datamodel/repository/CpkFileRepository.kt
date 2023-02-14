package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

interface CpkFileRepository {
    fun exists(em: EntityManager, cpkChecksum: SecureHash): Boolean
    fun put(em: EntityManager, cpkFile: CpkFile)
    fun findById(em: EntityManager, fileChecksums: List<SecureHash>): List<CpkFile>
    fun findById(em: EntityManager, fileChecksum: SecureHash): CpkFile

    /**
     * Returns all cpk files
     * @param fileChecksumsToExclude A list of checksum of files that should be excluded from the results list
     */
    fun findAll(em: EntityManager, fileChecksumsToExclude: List<SecureHash> = emptyList()): List<CpkFile>
}
