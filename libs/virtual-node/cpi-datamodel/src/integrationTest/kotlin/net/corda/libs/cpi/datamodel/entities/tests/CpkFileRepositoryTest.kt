package net.corda.libs.cpi.datamodel.entities.tests

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.TypedQuery
import net.corda.libs.cpi.datamodel.entities.internal.CpkFileEntity
import net.corda.libs.cpi.datamodel.repository.CpkFileRepositoryImpl
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.eq

class CpkFileRepositoryTest {
    private val cpkFileRepository = CpkFileRepositoryImpl()

    private val emFactory = mock<EntityManagerFactory>()

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
        val cpkFile = cpkFileRepository.findById(em, checksum)

        assertThat(cpkFile.fileChecksum).isEqualTo(checksum)
        assertThat(cpkFile.data).isEqualTo(bytes)
    }
}
