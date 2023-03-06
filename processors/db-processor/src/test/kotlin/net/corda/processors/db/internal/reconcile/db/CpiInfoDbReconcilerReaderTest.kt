package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.cpi.datamodel.entities.CpiMetadataEntity
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import kotlin.streams.toList
import net.corda.libs.cpi.datamodel.entities.CpiCpkEntity
import net.corda.libs.cpi.datamodel.entities.CpiCpkKey
import net.corda.libs.cpi.datamodel.entities.CpkMetadataEntity

class CpiInfoDbReconcilerReaderTest {
    private val random = Random(0)

    // TODO - we should maybe have a generator for this dummy data somewhere reusable?
    private val dummyCpkMetadata = CpkMetadata(
        CpkIdentifier(
            "SomeName",
            "1.0", SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
        ),
        CpkManifest(CpkFormatVersion(2, 3)),
        "mainBundle.jar",
        listOf("library.jar"),
        CordappManifest(
            "net.cordapp.Bundle",
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
        SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes)),
        emptySet(),
        Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

    private val dummyCpk =
        CpkMetadataEntity(
            "SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90",
            "test-cpk",
            "2.3.4",
            "SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90",
            "1.0",
            dummyCpkMetadata.toJsonAvro(),
            isDeleted = false
        )

    private val dummyCpiMetadataEntity =
        mock<CpiMetadataEntity>() {
            whenever(it.name).then { "test-cpi" }
            whenever(it.version).then { "1.2.3" }
            whenever(it.signerSummaryHash).then { "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA" }
            whenever(it.fileName).then { "test-cpi.cpi" }
            whenever(it.fileChecksum).then { "SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90" }
            whenever(it.groupPolicy).then { "{}" }
            whenever(it.groupId).then { "group-id" }
            whenever(it.fileUploadRequestId).then { "request-id" }
            whenever(it.isDeleted).then { false }
            whenever(it.cpks).then {
                listOf(dummyCpk).map { cpkMetadataEntity ->
                    CpiCpkEntity(
                        CpiCpkKey(
                            "test-cpi",
                            "1.2.3",
                            "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
                            cpkMetadataEntity.cpkFileChecksum
                        ),
                        "${cpkMetadataEntity.cpkName}.cpk",
                        cpkMetadataEntity
                    )
                }.toSet()
            }
        }

    @Test
    fun `doGetAllVersionedRecords converts db data to version records`() {
        val typeQuery = mock<TypedQuery<CpiMetadataEntity>>()
        whenever(typeQuery.resultStream).thenReturn(Stream.of(dummyCpiMetadataEntity))
        val em = mock<EntityManager>()
        whenever(em.transaction).thenReturn(mock())
        whenever(em.createQuery(any(), any<Class<CpiMetadataEntity>>())).thenReturn(typeQuery)
        val reconciliationContext = mock<ReconciliationContext>()
        whenever(reconciliationContext.getOrCreateEntityManager()).thenReturn(em)

        val versionedRecords = getAllCpiInfoDBVersionedRecords(reconciliationContext).toList()
        val record = versionedRecords.single()

        val expectedId = CpiIdentifier(
            dummyCpiMetadataEntity.name,
            dummyCpiMetadataEntity.version,
            SecureHash.parse(dummyCpiMetadataEntity.signerSummaryHash)
        )

        assertThat(record.key).isEqualTo(expectedId)
        assertThat(record.value.cpiId).isEqualTo(expectedId)

        assertThat(record.value.fileChecksum).isEqualTo(SecureHash.parse(dummyCpiMetadataEntity.fileChecksum))
        assertThat(record.value.groupPolicy).isEqualTo(dummyCpiMetadataEntity.groupPolicy)
        assertThat(record.value.cpksMetadata).containsExactly(dummyCpkMetadata)
    }
}
