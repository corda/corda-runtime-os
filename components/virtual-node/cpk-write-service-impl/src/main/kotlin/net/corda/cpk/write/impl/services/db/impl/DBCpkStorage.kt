package net.corda.cpk.write.impl.services.db.impl

import net.corda.cpk.write.impl.services.db.CpkStorage
import net.corda.cpk.write.impl.CpkChecksumData
import java.util.stream.Stream
import javax.persistence.EntityManagerFactory

// Just reads the cpk from the backed database
class DBCpkStorage(val entityManagerFactory: EntityManagerFactory) : CpkStorage {

    override fun getAllCpkInfo(): Stream<CpkChecksumData> {
        TODO("Not yet implemented")
    }
}