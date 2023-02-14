package net.corda.cpk.write.impl.services.db.impl

import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.libs.cpi.datamodel.CpkFile
import net.corda.libs.cpi.datamodel.repository.CpkFileRepositoryImpl
import net.corda.orm.utils.transaction
import net.corda.v5.crypto.SecureHash
import org.slf4j.LoggerFactory
import javax.persistence.EntityManagerFactory

// Consider moving following queries in here to be Named queries at entities site so that we save an extra Integration test
class DBCpkStorage(private val entityManagerFactory: EntityManagerFactory) : CpkStorage {

    companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val cpkFileRepository = CpkFileRepositoryImpl()
    }

    override fun getAllCpkFileIds(fileChecksumsToExclude: List<SecureHash>): List<SecureHash> {
        return entityManagerFactory.createEntityManager().transaction { em ->
            cpkFileRepository.findAll(em, fileChecksumsToExclude).map { it.fileChecksum }
        }
    }

    override fun getCpkFileById(fileChecksum: SecureHash): CpkFile {
        return entityManagerFactory.createEntityManager().transaction {
            cpkFileRepository.findById(it, fileChecksum)
        }
    }
}
