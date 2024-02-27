package net.corda.processors.db.internal.reconcile.db

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Random
import javax.persistence.EntityManager
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.v5.crypto.DigestAlgorithmName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

class CpiReconcilerTest {
    private val random = Random(0)

    // TODO - we should maybe have a generator for this dummy data somewhere reusable?
    private val dummyCpkMetadata = CpkMetadata(
        CpkIdentifier(
            "SomeName",
            "1.0", SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
        ),
        CpkManifest(CpkFormatVersion(2, 3)),
        "mainBundle.jar",
        listOf("library.jar"),
        CordappManifest(
            "com.r3.corda.Bundle",
            "1.2.3",
            12,
            34,
            CordappType.WORKFLOW,
            "someName",
            "R3",
            42,
            "some license",
            mapOf(
                "Corda-Contract-Classes" to "contractClass1, contractClass2",
                "Corda-Flow-Classes" to "flowClass1, flowClass2"
            ),
        ),
        CpkType.CORDA_API,
        SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes)),
        emptySet(),
        Instant.now().truncatedTo(ChronoUnit.MILLIS),
        externalChannelsConfig = "{}"
    )

    private val dummyCpiMetadata =
        mock<CpiMetadata>() {
            whenever(it.cpiId).then {
                CpiIdentifier(
                    "test-cpi",
                    "1.2.3",
                    parseSecureHash("SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA")
                )
            }
            whenever(it.fileChecksum).then { parseSecureHash("SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90") }
            whenever(it.groupPolicy).then { "{}" }
            whenever(it.cpksMetadata).then { setOf(dummyCpkMetadata) }
        }

    private val cpiMetadataRepositoryMock = mock<CpiMetadataRepository>(verboseLogging=true) {
        on { findAll(any()) }.doReturn(
            listOf(Triple(1, false, dummyCpiMetadata)).stream()
        )
    }

    private val cpiReconcilerMock = CpiReconciler(mock(), mock(), mock(), mock(), mock(), cpiMetadataRepositoryMock)

    @Test
    fun `doGetAllVersionedRecords converts db data to version records`() {
        val em = mock<EntityManager>()
        whenever(em.transaction).thenReturn(mock())

        val entityManagerFactory: EntityManagerFactory = mock()
        whenever(entityManagerFactory.createEntityManager()).thenReturn(em)
        
        val dbConnectionManager: DbConnectionManager = mock()
        whenever(dbConnectionManager.getClusterEntityManagerFactory()).thenReturn(entityManagerFactory)
        val reconciliationContext = ClusterReconciliationContext(dbConnectionManager)

        val versionedRecords = cpiReconcilerMock.getAllCpiInfoDBVersionedRecords(reconciliationContext).toList()
        val record = versionedRecords.single()


        assertThat(record.key).isEqualTo(dummyCpiMetadata.cpiId)
        assertThat(record.value.cpiId).isEqualTo(dummyCpiMetadata.cpiId)

        assertThat(record.value.fileChecksum).isEqualTo(dummyCpiMetadata.fileChecksum)
        assertThat(record.value.groupPolicy).isEqualTo(dummyCpiMetadata.groupPolicy)
        assertThat(record.value.cpksMetadata).containsExactly(dummyCpkMetadata)
    }
}
