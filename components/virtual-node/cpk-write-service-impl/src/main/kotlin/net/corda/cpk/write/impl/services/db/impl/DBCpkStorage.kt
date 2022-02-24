package net.corda.cpk.write.impl.services.db.impl

import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.services.db.CpkChecksumToData
import net.corda.libs.cpi.datamodel.CpkDataEntity
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import java.util.stream.Collectors
import javax.persistence.EntityManagerFactory

// Consider moving following queries in here to be Named queries at entities site so that we save an extra Integration test
class DBCpkStorage(private val entityManagerFactory: EntityManagerFactory) : CpkStorage {

    override fun getCpkIdsNotIn(checksums: Set<SecureHash>): Set<SecureHash> {
        return entityManagerFactory.createEntityManager().transaction {
            val cpkChecksumsStream = it.createQuery(
                "SELECT cpk.fileChecksum FROM ${CpkDataEntity::class.simpleName} cpk " +
                        "WHERE cpk.fileChecksum NOT IN (:checksums)",
                String::class.java
            )
                .setParameter(
                    "checksums",
                    if (checksums.isEmpty()) "" else checksums.map { checksum -> checksum.toString() })
                .resultStream

            cpkChecksumsStream.map { checksumString ->
                SecureHash.create(checksumString)
            }.collect(Collectors.toSet())
        }
    }

    override fun getCpkDataByCpkId(checksum: SecureHash): CpkChecksumToData {
        return entityManagerFactory.createEntityManager().transaction {
            val cpk = it.find(
                CpkDataEntity::class.java,
                checksum.toString()
            )
            CpkChecksumToData(SecureHash.create(cpk.fileChecksum), cpk.data)
        }
    }
}