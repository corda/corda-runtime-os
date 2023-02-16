package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

interface CpkFileRepository {
    fun exists(em: EntityManager, cpkChecksum: SecureHash): Boolean
    fun put(em: EntityManager, cpkFile: CpkFile)
    fun findById(em: EntityManager, fileChecksums: List<String>): List<CpkFile>
    fun findById(em: EntityManager, fileChecksum: SecureHash): CpkFile
    fun findByIdNotIn(em: EntityManager, fileChecksums: List<SecureHash>): List<CpkFile>
}