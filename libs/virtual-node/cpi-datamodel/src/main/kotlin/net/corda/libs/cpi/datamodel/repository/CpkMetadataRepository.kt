package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.cpi.datamodel.CpkMetadataLite
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

interface CpkMetadataRepository {

    fun findById(em: EntityManager, cpkFileChecksums: List<SecureHash>): List<CpkMetadataLite>
}