package net.corda.libs.packaging.core

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Random

class ConvertersTest {

    private val random = Random(0)

    private val cpiId = CpiIdentifier("SomeName",
        "1.0",
        SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
    )
    private val cpkId = CpkIdentifier("SomeName",
        "1.0", SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)))
    private val cpkDependencyId = CpkIdentifier("SomeName 2",
        "1.0", SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)))
    private val cpkType = CpkType.CORDA_API
    private val cpkFormatVersion = CpkFormatVersion(2, 3)
    private val cpkManifest = CpkManifest(CpkFormatVersion(2, 3))
    private val manifestCordappInfo = ManifestCorDappInfo("someName", "R3", 42, "some license")
    private val cordappManifest = CordappManifest(
        "net.corda.Bundle",
        "1.2.3",
        12,
        34,
        ManifestCorDappInfo("someName", "R3", 42, "some license"),
        ManifestCorDappInfo("someName", "R3", 42, "some license"),
        mapOf("Corda-Contract-Classes" to "contractClass1, contractClass2",
            "Corda-Flow-Classes" to "flowClass1, flowClass2"),
    )

    private val cpkMetadata = CpkMetadata(
        cpkId,
        cpkManifest,
        "mainBundle.jar",
        listOf("library.jar"),
        listOf(cpkDependencyId),
        cordappManifest,
        cpkType,
        SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
        emptySet()
    )

    private companion object {

        fun assertCpkMetadataEquals(cpkMetadata1 : CpkMetadata, cpkMetadata2 : CpkMetadata) {
            Assertions.assertEquals(cpkMetadata1.cpkId, cpkMetadata2.cpkId)
            Assertions.assertEquals(cpkMetadata1.mainBundle, cpkMetadata2.mainBundle)
            Assertions.assertEquals(cpkMetadata1.libraries, cpkMetadata2.libraries)
            Assertions.assertEquals(cpkMetadata1.dependencies, cpkMetadata2.dependencies)
            assertCordappManifestEquals(cpkMetadata1.cordappManifest, cpkMetadata2.cordappManifest)
            Assertions.assertEquals(cpkMetadata1.type, cpkMetadata2.type)
            Assertions.assertEquals(cpkMetadata1.fileChecksum, cpkMetadata2.fileChecksum)
            Assertions.assertEquals(cpkMetadata1.cordappCertificates, cpkMetadata2.cordappCertificates)
        }

        fun assertCordappManifestEquals(m1 : CordappManifest, m2 : CordappManifest) {
            sequenceOf(
                CordappManifest::bundleSymbolicName,
                CordappManifest::bundleVersion,
                CordappManifest::minPlatformVersion,
                CordappManifest::targetPlatformVersion,
                CordappManifest::contractInfo,
                CordappManifest::workflowInfo,
                CordappManifest::attributes,
            ).forEach {
                Assertions.assertEquals(it(m1), it(m2))
            }
        }

        fun assertCPIMetadataEquals(cpiMetadata1: CpiMetadata, cpiMetadata2: CpiMetadata) {
            Assertions.assertEquals(cpiMetadata1.cpiId, cpiMetadata2.cpiId)
            Assertions.assertEquals(cpiMetadata1.fileChecksum, cpiMetadata2.fileChecksum)
            Assertions.assertEquals(cpiMetadata1.cpksMetadata.size, cpiMetadata2.cpksMetadata.size)
            cpiMetadata1.cpksMetadata.asSequence().zip(cpiMetadata2.cpksMetadata.asSequence()).forEach {
                Assertions.assertEquals(it.first, it.second)
            }
            Assertions.assertEquals(cpiMetadata1.groupPolicy, cpiMetadata2.groupPolicy)
        }
    }

    @Test
    fun `CPK․Identifier round trip`() {
        val original = cpkId
        val avroObject = original.toAvro()
        val cordaObject = CpkIdentifier.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Identifier without signerSummaryHash round trip`() {
        val original = CpkIdentifier("SomeName", "1.0", null)
        val avroObject = original.toAvro()
        val cordaObject = CpkIdentifier.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Type round trip`() {
        val original = cpkType
        val avroObject = original.toAvro()
        val cordaObject = CpkType.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․FormatVersion round trip`() {
        val original = cpkFormatVersion
        val avroObject = original.toAvro()
        val cordaObject = CpkFormatVersion.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Manifest round trip`() {
        val original = cpkManifest
        val avroObject = original.toAvro()
        val cordaObject = CpkManifest(
            CpkFormatVersion.fromAvro(avroObject.version)
        )
        Assertions.assertEquals(original.cpkFormatVersion, cordaObject.cpkFormatVersion)
    }

    @Test
    fun `ManifestCordappInfo round trip`() {
        val original = manifestCordappInfo
        val avroObject = original.toAvro()
        val cordaObject =
            ManifestCorDappInfo(avroObject.shortName, avroObject.vendor, avroObject.versionId, avroObject.license)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CordappManifest round trip`() {
        val original = cordappManifest
        val avroObject = original.toAvro()
        val cordaObject = CordappManifest.fromAvro(avroObject)
        assertCordappManifestEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Metadata round trip`() {
        val original = CpkMetadata(
            cpkId,
            cpkManifest,
            "mainBundle.jar",
            listOf("library.jar"),
            listOf(cpkId),
            cordappManifest,
            cpkType,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            emptySet()
        )
        val avroObject = original.toAvro()
        val cordaObject = CpkMetadata.fromAvro(avroObject)
        assertCpkMetadataEquals(original, cordaObject)
    }

    @Test
    fun `CPI․Identifier round trip`() {
        val original = cpiId
        val avroObject = original.toAvro()
        val cordaObject = CpiIdentifier.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPI․Identifier without signerSummaryHash round trip`() {
        val original = CpiIdentifier(
            "SomeName",
            "1.0",
            null
        )
        val avroObject = original.toAvro()
        val cordaObject = CpiIdentifier.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }


    @Test
    fun `CPI․Metadata round trip`() {
        val original = CpiMetadata(
            cpiId,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            listOf(cpkMetadata),
            "someString"
        )
        val avroObject = original.toAvro()
        val cordaObject = CpiMetadata.fromAvro(avroObject)
        assertCPIMetadataEquals(original, cordaObject)
    }
}
