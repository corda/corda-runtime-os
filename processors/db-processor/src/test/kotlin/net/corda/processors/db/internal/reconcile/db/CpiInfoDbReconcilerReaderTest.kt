package net.corda.processors.db.internal.reconcile.db

import net.corda.libs.cpi.datamodel.CpiCpkEntity
import net.corda.libs.cpi.datamodel.CpiCpkKey
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.cpi.datamodel.CpkMetadataEntity
import net.corda.libs.packaging.core.CordappManifest
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
import java.util.Random
import javax.persistence.EntityManager
import javax.persistence.TypedQuery
import kotlin.streams.toList
import net.corda.libs.cpi.datamodel.CpkKey
import net.corda.libs.packaging.core.CordappType
import java.util.UUID

class CpiInfoDbReconcilerReaderTest {
    private val random = Random(0)

    // TODO - we should maybe have a generator for this dummy data somewhere reusable?
    private fun genDummyCpkMetadata(cpiName: String, cpkName: String): CpkMetadata {
        return CpkMetadata(
            CpkIdentifier(
                cpkName,
                "1.0",
                SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
            ),
            CpkManifest(CpkFormatVersion(2, 3)),
            "mainBundle.jar",
            listOf("library.jar"),
            listOf(
                CpkIdentifier(
                    "SomeName 2",
                    "1.0",
                    SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
                )
            ),
            CordappManifest(
                "net.cordapp.Bundle",
                "1.2.3",
                12,
                34,
                CordappType.WORKFLOW,
                cpiName,
                "R3",
                42,
                "some license",
                mapOf(
                    "Corda-Contract-Classes" to "contractClass1, contractClass2",
                    "Corda-Flow-Classes" to "flowClass1, flowClass2"
                ),
            ),
            CpkType.CORDA_API,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            emptySet(),
            Instant.now().truncatedTo(ChronoUnit.MILLIS)
        )
    }

    // Make data look valid in format
    fun genCpk(cpiName: String): CpkMetadataEntity {
        val cpkName = "test-cpk-${UUID.randomUUID()}"
        return CpkMetadataEntity(
            CpkKey(cpkName, "2.3.4", "SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90"),
            "SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90",
            "1.0",
            genDummyCpkMetadata(cpiName, cpkName).toJsonAvro(),
            isDeleted = false
        )
    }

    private val dummyCpiMetadataEntities = (1..5).map{
        val name = "test-cpi-${UUID.randomUUID()}"
        CpiMetadataEntity(
            name,
                version = "1.2.3",
                signerSummaryHash = "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
                fileName = "test-cpi.cpi",
                fileChecksum = "SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90",
                groupPolicy = "{}",
                groupId = "group-id",
                fileUploadRequestId = "request-id",
                isDeleted = false,
                cpks = (1..5).map {
                    genCpk(name).run {
                        CpiCpkEntity(
                            CpiCpkKey(
                                "test-cpi-$it",
                                "1.2.3",
                                "SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA",
                                id.cpkName,
                                id.cpkVersion,
                                id.cpkSignerSummaryHash
                            ),
                            "${id.cpkName}.cpk",
                            cpkFileChecksum,
                            this
                        )
                    }

                }.toSet()
        )
    }


    @Test
    fun `doGetAllVersionedRecords converts db data to version records`() {
        val typeQuery = mock<TypedQuery<CpiMetadataEntity>>()
        whenever(typeQuery.resultStream).thenReturn(dummyCpiMetadataEntities.stream())
        val em = mock<EntityManager>()
        whenever(em.transaction).thenReturn(mock())
        whenever(em.createQuery(any(), any<Class<CpiMetadataEntity>>())).thenReturn(typeQuery)

        val versionedRecords = getAllCpiInfoDBVersionedRecords(em).toList()
        assertThat(versionedRecords.size).isEqualTo(dummyCpiMetadataEntities.size)

        dummyCpiMetadataEntities.forEach {
            val dummyCpiMetadataEntity = it
            val record = versionedRecords.single { vr ->
                CpiIdentifier(
                    dummyCpiMetadataEntity.name,
                    dummyCpiMetadataEntity.version,
                    SecureHash.parse(dummyCpiMetadataEntity.signerSummaryHash)
                ) == vr.key
            }

            val expectedId = CpiIdentifier(
                dummyCpiMetadataEntity.name,
                dummyCpiMetadataEntity.version,
                SecureHash.parse(dummyCpiMetadataEntity.signerSummaryHash))

            assertThat(record.key).isEqualTo(expectedId)
            assertThat(record.value.cpiId).isEqualTo(expectedId)

            assertThat(record.value.fileChecksum).isEqualTo(SecureHash.parse(dummyCpiMetadataEntity.fileChecksum))
            assertThat(record.value.groupPolicy).isEqualTo(dummyCpiMetadataEntity.groupPolicy)
            assertThat(it.cpks.map { cpk -> cpk.metadata.id.cpkName }).isEqualTo(record.value.cpksMetadata.map { cpk -> cpk.cpkId.name })
        }
    }
}