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

class DBCpkStorageTest {
    private lateinit var dbCpkStorage: DBCpkStorage

    private lateinit var emFactory: EntityManagerFactory

    companion object {
        const val UN_PARSABLE_HASH = "BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FC"
    }

    @BeforeEach
    fun setUp() {
        emFactory = mock()
        dbCpkStorage = DBCpkStorage(emFactory)
    }

    @Test
    fun `on get cpk ids with empty string for checksum does not include it to the returned checksums`() {
        val mockTypedQuery = mock<TypedQuery<String>>()
        whenever(mockTypedQuery.setParameter(any<String>(), any())).thenReturn(mockTypedQuery)
        whenever(mockTypedQuery.resultList).thenReturn(listOf(""))
        val em = mock<EntityManager>()
        whenever(em.createQuery(any(), any<Class<String>>())).thenReturn(mockTypedQuery)
        whenever(em.transaction).thenReturn(mock())
        whenever(emFactory.createEntityManager()).thenReturn(em)

        val cpkIds = dbCpkStorage.getCpkIdsNotIn(listOf())
        assertThat(cpkIds).isEmpty()
    }

    @Test
    fun `on get cpk ids with invalid checksum format for checksum does not include it to the returned checksums`() {
        val mockTypedQuery = mock<TypedQuery<String>>()
        whenever(mockTypedQuery.setParameter(any<String>(), any())).thenReturn(mockTypedQuery)
        whenever(mockTypedQuery.resultList).thenReturn(listOf(UN_PARSABLE_HASH))
        val em = mock<EntityManager>()
        whenever(em.createQuery(any(), any<Class<String>>())).thenReturn(mockTypedQuery)
        whenever(em.transaction).thenReturn(mock())
        whenever(emFactory.createEntityManager()).thenReturn(em)

        val cpkIds = dbCpkStorage.getCpkIdsNotIn(listOf())
        assertThat(cpkIds).isEmpty()
    }
}