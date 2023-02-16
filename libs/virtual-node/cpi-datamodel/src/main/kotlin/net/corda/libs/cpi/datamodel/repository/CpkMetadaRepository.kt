package net.corda.libs.cpi.datamodel.repository

import net.corda.v5.crypto.SecureHash
import net.corda.libs.cpi.datamodel.CpkMetadataLite
import javax.persistence.EntityManager

interface CpkMetadaRepository {

    fun findById(em: EntityManager, fileChecksums: List<SecureHash>): List<CpkMetadataLite>
}