package net.corda.cpk.write.impl.services.db.impl

import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.db.CpkChecksumToData
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.orm.utils.transaction
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import java.util.stream.Collectors
import javax.persistence.EntityManagerFactory

// Consider moving following queries in here to be Named queries at entities site so that we save an extra Integration test
class DBCpkStorage(private val entityManagerFactory: EntityManagerFactory) : CpkStorage {

    companion object {
        val logger = contextLogger()
    }

    override fun getCpkIdsNotIn(checksums: Set<SecureHash>): Set<SecureHash> {
        return entityManagerFactory.createEntityManager().transaction {
            val cpkChecksumsStream = it.createQuery(
                "SELECT cpk.fileChecksum FROM ${CpkDataEntity::class.simpleName} cpk " +
                        "WHERE cpk.fileChecksum NOT IN (:checksums)",
                String::class.java
            )
                .setParameter(
                    "checksums",
                    if (checksums.isEmpty()) "null" else checksums.map { checksum -> checksum.toString() })
                .resultList

            cpkChecksumsStream.mapNotNull { checksumString ->
                if (checksumString == null || checksumString.isEmpty()) {
                    logger.warn("Found invalid value for CPK checksum: $checksumString")
                    null
                } else {
                    try {
                        SecureHash.create(checksumString)
                    } catch (e: Exception) {
                        logger.warn("Failed when tried to parse a CPK checksum with", e)
                        null
                    }
                }
            }.toSet()
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