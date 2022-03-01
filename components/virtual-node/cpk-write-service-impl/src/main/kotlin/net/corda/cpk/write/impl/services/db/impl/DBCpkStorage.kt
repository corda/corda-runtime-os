package net.corda.cpk.write.impl.services.db.impl

import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.db.CpkChecksumToData
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.libs.cpi.datamodel.findCpkChecksumsNotIn
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import javax.persistence.EntityManagerFactory

// Consider moving following queries in here to be Named queries at entities site so that we save an extra Integration test
class DBCpkStorage(private val entityManagerFactory: EntityManagerFactory) : CpkStorage {

    companion object {
        val logger = contextLogger()
    }

    override fun getCpkIdsNotIn(checksums: List<SecureHash>): List<SecureHash> {
        return entityManagerFactory.createEntityManager().transaction { em ->
            val cpkChecksumsStr = em.findCpkChecksumsNotIn(checksums.map { it.toString() })

            cpkChecksumsStr.mapNotNull { checksumStr ->
                if (checksumStr.isEmpty()) {
                    logger.warn("Found empty value for CPK checksum")
                    null
                } else {
                    try {
                        SecureHash.create(checksumStr)
                    } catch (e: Exception) {
                        logger.warn("Failed when tried to parse a CPK checksum with", e)
                        null
                    }
                }
            }
        }
    }

    override fun getCpkDataByCpkId(checksum: SecureHash): CpkChecksumToData {
        return entityManagerFactory.createEntityManager().transaction {
            val cpkDataEntity = it.find(
                CpkDataEntity::class.java,
                checksum.toString()
            )
            CpkChecksumToData(SecureHash.create(cpkDataEntity.fileChecksum), cpkDataEntity.data)
        }
    }
}