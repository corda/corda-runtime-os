package net.corda.libs.cpi.datamodel.repository

import javax.persistence.EntityManager
import net.corda.v5.crypto.SecureHash
import net.corda.libs.packaging.core.CpkMetadata

interface CpkRepository {
    fun findById(em: EntityManager, cpkFileChecksum: SecureHash):  Pair<Int, CpkMetadata>?
}
