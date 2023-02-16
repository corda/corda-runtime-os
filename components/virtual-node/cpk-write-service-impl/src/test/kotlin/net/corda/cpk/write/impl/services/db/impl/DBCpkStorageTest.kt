package net.corda.cpk.write.impl.services.db.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.TypedQuery
import net.corda.libs.cpi.datamodel.entities.CpkFileEntity
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.eq

class DBCpkStorageTest {
    private lateinit var dbCpkStorage: DBCpkStorage

    private lateinit var emFactory: EntityManagerFactory

    @BeforeEach
    fun setUp() {
        emFactory = mock()
        dbCpkStorage = DBCpkStorage(emFactory)
    }

    @Test
    fun `get cpk data by checksum uses named query`() {
        val bytes = "sometext".toByteArray()
        val cpkFileAsList = CpkFileEntity(
            "SHA-256:1234567890",
            bytes
        )

        val mockTypedQuery = mock<TypedQuery<CpkFileEntity>>()
        whenever(mockTypedQuery.setParameter(eq("checksum"), eq("SHA-256:1234567890"))).thenReturn(mockTypedQuery)
        whenever(mockTypedQuery.singleResult).thenReturn(cpkFileAsList)
        val em = mock<EntityManager>()
        whenever(em.createQuery(any(), any<Class<CpkFileEntity>>())).thenReturn(mockTypedQuery)
        whenever(em.transaction).thenReturn(mock())
        whenever(emFactory.createEntityManager()).thenReturn(em)

        val checksum = SecureHash.parse("SHA-256:1234567890")
        val cpkFile = dbCpkStorage.getCpkFileById(checksum)

        assertThat(cpkFile.fileChecksum).isEqualTo(checksum)
        assertThat(cpkFile.data).isEqualTo(bytes)
    }
}