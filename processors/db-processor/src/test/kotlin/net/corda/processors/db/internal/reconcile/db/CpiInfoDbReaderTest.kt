package net.corda.processors.db.internal.reconcile.db

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.TypedQuery
import kotlin.streams.toList

class CpiInfoDbReaderTest {
    lateinit var cpiInfoDbReader: CpiInfoDbReader
    lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    lateinit var dbConnectionManager: DbConnectionManager

    companion object {
        const val DUMMY_HASH = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA"
    }

    @BeforeEach
    fun setUp() {
        coordinatorFactory = mock()
        dbConnectionManager = mock()
        cpiInfoDbReader = CpiInfoDbReader(coordinatorFactory, dbConnectionManager)
    }

    private val dummyCpiMetadataEntity =
        CpiMetadataEntity(
            "", "",
            DUMMY_HASH, "",
            DUMMY_HASH, "", "", "",
            false
        )

    @Test
    fun `doGetAllVersionedRecords converts db data to version records`() {
        val typeQuery = mock<TypedQuery<CpiMetadataEntity>>()
        whenever(typeQuery.resultStream).thenReturn(Stream.of(dummyCpiMetadataEntity))
        val entityManager = mock<EntityManager>()
        whenever(entityManager.transaction).thenReturn(mock())
        whenever(entityManager.createQuery(any(), any<Class<CpiMetadataEntity>>())).thenReturn(typeQuery)
        val entityManagerFactory = mock<EntityManagerFactory>()
        whenever(entityManagerFactory.createEntityManager()).thenReturn(entityManager)
        cpiInfoDbReader.entityManagerFactory = entityManagerFactory


        val versionedRecords = cpiInfoDbReader.doGetAllVersionedRecords().toList()
        val cpiMetdata = versionedRecords.single().value
        // Could check more fields if its worth it...
        assertEquals(DUMMY_HASH, cpiMetdata.cpiId.signerSummaryHash!!.toString())
        assertEquals(DUMMY_HASH, cpiMetdata.fileChecksum.toString())
        println(versionedRecords)
    }
}