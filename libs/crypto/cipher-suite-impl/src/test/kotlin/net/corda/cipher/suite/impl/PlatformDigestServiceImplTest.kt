package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.DigestScheme
import net.corda.crypto.impl.DoubleSHA256Digest
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.assertEquals

class PlatformDigestServiceImplTest {

    companion object {
        private val SHA2_256 = DigestAlgorithmName.SHA2_256
        private val SHA2_384 = DigestAlgorithmName.SHA2_384
        private val SHA2_512 = DigestAlgorithmName.SHA2_512
        private val SHA3_256 = DigestAlgorithmName("SHA3-256")
        private val SHA3_384 = DigestAlgorithmName("SHA3-384")
        private val SHA3_512 = DigestAlgorithmName("SHA3-512")
        private val CUSTOM_DIGEST = DigestAlgorithmName(DoubleSHA256Digest.ALGORITHM)

        private lateinit var digestService: PlatformDigestServiceImpl
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
            digestService = PlatformDigestServiceImpl(schemeMetadata)
        }

        @JvmStatic
        fun majorDigests() = listOf(
            SHA2_256, SHA2_384, SHA2_512,
            SHA3_256, SHA3_384, SHA3_512
        )

        @JvmStatic
        fun digests() = schemeMetadata.digests

        @JvmStatic
        fun bannedDigests() = CipherSchemeMetadata.BANNED_DIGESTS.map { DigestAlgorithmName(it) }
    }

    @ParameterizedTest
    @MethodSource("bannedDigests")
    fun `Should not be using unsafe (banned) digest algorithms`(
        algorithmName: DigestAlgorithmName
    ) {
        assertThrows<IllegalArgumentException> {
            digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), algorithmName)
        }
    }

    @ParameterizedTest
    @MethodSource("majorDigests")
    fun `Should support major digests algorithms`(
        algorithmName: DigestAlgorithmName
    ) {
        assertTrue(schemeMetadata.digests.any { it.algorithmName == algorithmName.name })
        val message = "abc".toByteArray()
        val hash = digestService.hash(message, algorithmName)
        assertTrue(hash.bytes.isNotEmpty())
        assertEquals(algorithmName.name, hash.algorithm)
        assertTrue(digestService.digestLength(algorithmName) > 0)
        assertEquals(digestService.digestLength(algorithmName), hash.size)
        assertEquals(
            digestService.hash(message, algorithmName),
            digestService.hash(message, algorithmName))
    }

    @Test
    fun `Should calculate sha2-256 secure hash`() {
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), SHA2_256)
        assertEquals(32, hash.size)
        assertEquals(32, digestService.digestLength(SHA2_256))
        assertEquals(
            SecureHash.parse("SHA-256:6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F"),
            hash
        )
        assertEquals("SHA-256:6D1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581F", hash.toString())
    }

    @Test
    fun `Should calculate sha2-384 secure hash`() {
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), SHA2_384)
        assertEquals(48, hash.size)
        assertEquals(48, digestService.digestLength(SHA2_384))
        assertEquals(
            SecureHash.parse(
            "SHA-384:5E3DBD33BEC467F625E28D4C5DF90CAACEA722F2DBB2AE9EF9C59EF4FB0FA31A070F5911156713F6AA0FCB09186B78FF"),
            hash
        )
        assertEquals(
    "SHA-384:5E3DBD33BEC467F625E28D4C5DF90CAACEA722F2DBB2AE9EF9C59EF4FB0FA31A070F5911156713F6AA0FCB09186B78FF",
            hash.toString())
    }

    @Test
    fun `Should calculate sha2-512 secure hash`() {
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), SHA2_512)
        assertEquals(64, hash.size)
        assertEquals(64, digestService.digestLength(SHA2_512))
        assertEquals(
            SecureHash.parse(
            "SHA-512:A0F54F81E7FC7387989E1582E83F3A9051151E380F67E0F71D5CEE266B582F4105E08E8707A554FC9D3A6B3BEA1ECA" +
                    "8CC4E6BA1CF4DE78D8822B3EA724DE9D6C"),
            hash)
        assertEquals("SHA-512:A0F54F81E7FC7387989E1582E83F3A9051151E380F67E0F71D5CEE266B582F4105E08E8707A5" +
                "54FC9D3A6B3BEA1ECA8CC4E6BA1CF4DE78D8822B3EA724DE9D6C", hash.toString())
    }

    @Test
    fun `Should calculate sha3-256 secure hash`() {
        assumeTrue(JavaVersion.isVersionAtLeast(JavaVersion.JAVA_11))
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), SHA3_256)
        assertEquals(32, hash.size)
        assertEquals(32, digestService.digestLength(SHA3_256))
        assertEquals(
            SecureHash.parse(
            "SHA3-256:A243D53F7273F4C92ED901A14F11B372FDF6FF69583149AFD4AFA24BF17A8880"),
            hash
        )
        assertEquals("SHA3-256:A243D53F7273F4C92ED901A14F11B372FDF6FF69583149AFD4AFA24BF17A8880",
            hash.toString()
        )
    }

    @Test
    fun `Should calculate sha3-384 secure hash`() {
        assumeTrue(JavaVersion.isVersionAtLeast(JavaVersion.JAVA_11))
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), SHA3_384)
        assertEquals(48, hash.size)
        assertEquals(48, digestService.digestLength(SHA3_384))
        assertEquals(
            SecureHash.parse("SHA3-384:AB698010362BFEDB89BCC8800F7E1410A92D83D5B80B99969A079D1FF1BC0" +
                "7CF817998E855B6D3A56797F1182AC24307"),
            hash
        )
        assertEquals("SHA3-384:AB698010362BFEDB89BCC8800F7E1410A92D83D5B80B99969A079D1FF1BC07CF817998E855" +
                "B6D3A56797F1182AC24307",
            hash.toString()
        )
    }

    @Test
    fun `Should calculate sha3-512 secure hash`() {
        assumeTrue(JavaVersion.isVersionAtLeast(JavaVersion.JAVA_11))
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), SHA3_512)
        assertEquals(64, hash.size)
        assertEquals(64, digestService.digestLength(SHA3_512))
        assertEquals(
            SecureHash.parse("SHA3-512:20FDD4FAB7B85E6C9227C679588E1E62A781217C455AEC5792DA155736C2" +
                "7CAFC5989ECC6E6D7590BDBB57F9E4C945B16DB60E2D09C4F72C8D826A34A2D03C4E"),
            hash
        )
        assertEquals("SHA3-512:20FDD4FAB7B85E6C9227C679588E1E62A781217C455AEC5792DA155736C27CAFC5989ECC6" +
                "E6D7590BDBB57F9E4C945B16DB60E2D09C4F72C8D826A34A2D03C4E",
            hash.toString()
        )
    }

    @Test
    fun `Should calculate secure hash using custom factory`() {
        val hash = digestService.hash(byteArrayOf(0x64, -0x13, 0x42, 0x3a), CUSTOM_DIGEST)
        assertEquals(32, hash.size)
        assertEquals(32, digestService.digestLength(CUSTOM_DIGEST))
        assertEquals(
            SecureHash.parse(
                "SHA-256D:CB2A6BC131E59DC17DF10769ACBDFEC06965F0AFEAF1C3359E69CB915873E051"
            ),
            hash
        )
        assertEquals(
            "SHA-256D:CB2A6BC131E59DC17DF10769ACBDFEC06965F0AFEAF1C3359E69CB915873E051",
            hash.toString()
        )
    }

    @ParameterizedTest
    @MethodSource("digests")
    fun `Should not retain state between same-thread invocations for all supported digests`(
        digestScheme: DigestScheme
    ) {
        val algorithmName = DigestAlgorithmName(digestScheme.algorithmName)
        val message = "abc".toByteArray()
        val hash = digestService.hash(message, algorithmName)
        assertTrue(hash.bytes.isNotEmpty())
        assertEquals(algorithmName.name, hash.algorithm)
        assertTrue(digestService.digestLength(algorithmName) > 0)
        assertEquals(digestService.digestLength(algorithmName), hash.size)
        assertEquals(
            digestService.hash(message, algorithmName),
            digestService.hash(message, algorithmName))
    }

    @ParameterizedTest
    @MethodSource("digests")
    fun `Should calculate hash for array for all supported digests`(
        digestScheme: DigestScheme
    ) {
        val algorithmName = DigestAlgorithmName(digestScheme.algorithmName)
        val random = Random(17)
        for ( i in 1..100) {
            val len = random.nextInt(127, 277)
            val data = ByteArray(len)
            random.nextBytes(data)
            val actual = digestService.hash(data, algorithmName)
            val expected = MessageDigest.getInstance(
                algorithmName.name,
                schemeMetadata.providers[digestScheme.providerName]
            ).digest(data)
            assertEquals(algorithmName.name, actual.algorithm)
            assertArrayEquals(expected, actual.bytes)
        }
    }

    @ParameterizedTest
    @MethodSource("digests")
    fun `Should calculate hash for short input streams for all supported digests`(
        digestScheme: DigestScheme
    ) {
        val algorithmName = DigestAlgorithmName(digestScheme.algorithmName)
        val random = Random(17)
        for ( i in 1..100) {
            val len = random.nextInt(1, 100)
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = digestService.hash(stream, algorithmName)
            val expected = MessageDigest.getInstance(
                algorithmName.name,
                schemeMetadata.providers[digestScheme.providerName]
            ).digest(data)
            assertEquals(algorithmName.name, actual.algorithm)
            assertArrayEquals(expected, actual.bytes)
            assertArrayEquals(expected, actual.bytes)
        }
    }

    @ParameterizedTest
    @MethodSource("digests")
    fun `Should calculate hash for medium sized input streams for all supported digests`(
        digestScheme: DigestScheme
    ) {
        val algorithmName = DigestAlgorithmName(digestScheme.algorithmName)
        val random = Random(17)
        for ( i in 1..100) {
            val len = random.nextInt(375, 2074)
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = digestService.hash(stream, algorithmName)
            val expected = MessageDigest.getInstance(
                algorithmName.name,
                schemeMetadata.providers[digestScheme.providerName]
            ).digest(data)
            assertEquals(algorithmName.name, actual.algorithm)
            assertArrayEquals(expected, actual.bytes)
        }
    }

    @ParameterizedTest
    @MethodSource("digests")
    fun `Should calculate hash for large sized input streams for all supported digests`(
        digestScheme: DigestScheme
    ) {
        val algorithmName = DigestAlgorithmName(digestScheme.algorithmName)
        val random = Random(17)
        for ( i in 1..10) {
            val len = random.nextInt(37_794, 63_987)
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = digestService.hash(stream, algorithmName)
            val expected = MessageDigest.getInstance(
                algorithmName.name,
                schemeMetadata.providers[digestScheme.providerName]
            ).digest(data)
            assertEquals(algorithmName.name, actual.algorithm)
            assertArrayEquals(expected, actual.bytes)
        }
    }

    @ParameterizedTest
    @MethodSource("digests")
    fun `Should calculate hash for input streams with sizes around buffer size for all supported digests`(
        digestScheme: DigestScheme
    ) {
        val algorithmName = DigestAlgorithmName(digestScheme.algorithmName)
        val random = Random(17)
        for ( len in (DEFAULT_BUFFER_SIZE - 5)..((DEFAULT_BUFFER_SIZE + 5))) {
            val data = ByteArray(len)
            random.nextBytes(data)
            val stream = ByteArrayInputStream(data)
            val actual = digestService.hash(stream, algorithmName)
            val expected = MessageDigest.getInstance(
                algorithmName.name,
                schemeMetadata.providers[digestScheme.providerName]
            ).digest(data)
            assertEquals(algorithmName.name, actual.algorithm)
            assertArrayEquals(expected, actual.bytes)
        }
    }
    enum class JavaVersion(val versionString: String) {
        JAVA_11("11");

        companion object {
            fun isVersionAtLeast(version: JavaVersion): Boolean {
                return currentVersion.toFloat() >= version.versionString.toFloat()
            }

            private val currentVersion: String = System.getProperty("java.specification.version") ?:
            throw IllegalStateException("Unable to retrieve system property java.specification.version")
        }
    }
}
