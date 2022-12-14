package net.corda.libs.packaging.core

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ConvertersTest {

    private companion object {

        fun assertCpkMetadataEquals(cpkMetadata1 : CpkMetadata, cpkMetadata2 : CpkMetadata) {
            Assertions.assertEquals(cpkMetadata1.cpkId, cpkMetadata2.cpkId)
            Assertions.assertEquals(cpkMetadata1.mainBundle, cpkMetadata2.mainBundle)
            Assertions.assertEquals(cpkMetadata1.libraries, cpkMetadata2.libraries)
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
                CordappManifest::type,
                CordappManifest::shortName,
                CordappManifest::vendor,
                CordappManifest::versionId,
                CordappManifest::licence,
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
        val original = CpkMetaTestData.cpkId
        val avroObject = original.toAvro()
        val cordaObject = CpkIdentifier.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Type round trip`() {
        val original = CpkMetaTestData.cpkType
        val avroObject = original.toAvro()
        val cordaObject = CpkType.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․FormatVersion round trip`() {
        val original = CpkMetaTestData.cpkFormatVersion
        val avroObject = original.toAvro()
        val cordaObject = CpkFormatVersion.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Manifest round trip`() {
        val original = CpkMetaTestData.cpkManifest
        val avroObject = original.toAvro()
        val cordaObject = CpkManifest(
            CpkFormatVersion.fromAvro(avroObject.version)
        )
        Assertions.assertEquals(original.cpkFormatVersion, cordaObject.cpkFormatVersion)
    }

    @Test
    fun `CordappType round trip`() {
        val original = CpkMetaTestData.cordappType
        val avroObject = original.toAvro()
        val cordaObject = CordappType.fromAvro(avroObject)
        Assertions.assertEquals(original, cordaObject)
    }

    @Test
    fun `CordappManifest round trip`() {
        val original = CpkMetaTestData.cordappManifest
        val avroObject = original.toAvro()
        val cordaObject = CordappManifest.fromAvro(avroObject)
        assertCordappManifestEquals(original, cordaObject)
    }

    @Test
    fun `CPK․Metadata round trip`() {
        val original = CpkMetaTestData.create()
        val avroObject = original.toAvro()
        val cordaObject = CpkMetadata.fromAvro(avroObject)
        assertCpkMetadataEquals(original, cordaObject)
    }

    @Test
    fun `CPI․Identifier round trip`() {
        val original = CpkMetaTestData.cpiId
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
            CpkMetaTestData.cpiId,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(CpkMetaTestData.random::nextBytes)),
            listOf(CpkMetaTestData.create()),
            "someString",
            -1,
            CpkMetaTestData.currentTimeStamp
        )
        val avroObject = original.toAvro()
        val cordaObject = CpiMetadata.fromAvro(avroObject)
        assertCPIMetadataEquals(original, cordaObject)
    }
}
