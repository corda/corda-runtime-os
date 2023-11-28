package net.corda.libs.cpi.datamodel.repository

import net.corda.libs.packaging.core.CpkMetadata
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManager

interface CpkRepository {
    fun findById(em: EntityManager, cpkFileChecksum: SecureHash): Pair<Int, CpkMetadata>?
}
