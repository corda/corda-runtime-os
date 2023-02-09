package net.corda.processors.db.internal.reconcile.db

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.streams.toList
import net.corda.libs.packaging.core.*

class CpiInfoDbReconcilerReaderTest {
    private val random = Random(0)

    // TODO - we should maybe have a generator for this dummy data somewhere reusable?
    private val dummyCpkMetadata = CpkMetadata(
        CpkIdentifier(
            "SomeName",
            "1.0", SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
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
        SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
        emptySet(),
        Instant.now().truncatedTo(ChronoUnit.MILLIS)
    )

    private val mockCpiMetadata =
        mock<CpiMetadata>() {
            whenever(it.cpiId).then {
                CpiIdentifier(
                    "test-cpi",
                    "1.2.3",
                    SecureHash.parse("SHA-256:BFD76C0EBBD006FEE583410547C1887B0292BE76D582D96C242D2A792723E3FA")
                )
            }
            whenever(it.fileChecksum).then { SecureHash.parse("SHA-256:98AF8725385586B41FEFF205B4E05A000823F78B5F8F5C02439CE8F67A781D90") }
            whenever(it.cpksMetadata).then { listOf(dummyCpkMetadata) }
            whenever(it.groupPolicy).then { "{}" }
            whenever(it.version).then { -1 }
            whenever(it.timestamp).then { Instant.parse("2019-02-24T14:31:33.197021300Z") }
            whenever(it.isDeleted).then { true }
        }

    @Test
    fun `can convert db cpi metadata to versioned record`() {
        val mockCpiMetadataList = listOf(mockCpiMetadata)

        val versionedRecords = cpiMetadataToVersionedRecords(mockCpiMetadataList.stream())
        val record = versionedRecords.toList().single()

        assertThat(record.key).isEqualTo(mockCpiMetadata.cpiId)
        assertThat(record.value.cpiId).isEqualTo(mockCpiMetadata.cpiId)
        assertThat(record.value.fileChecksum).isEqualTo(mockCpiMetadata.fileChecksum)
        assertThat(record.value.groupPolicy).isEqualTo(mockCpiMetadata.groupPolicy)
        assertThat(record.value.cpksMetadata).containsExactly(mockCpiMetadata.cpksMetadata.first())
        assertThat(record.value.version).isEqualTo(mockCpiMetadata.version)
        assertThat(record.value.timestamp).isEqualTo(mockCpiMetadata.timestamp)
        assertThat(record.value.isDeleted).isEqualTo(mockCpiMetadata.isDeleted)

    }
}
