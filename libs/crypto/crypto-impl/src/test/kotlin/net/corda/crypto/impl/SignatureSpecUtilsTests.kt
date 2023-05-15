package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.CustomSignatureSpec
import net.corda.crypto.cipher.suite.ParameterizedSignatureSpec
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.InputStream
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.UUID

class SignatureSpecUtilsTests {
    companion object {
        private lateinit var digestService: PlatformDigestService

        @BeforeAll
        @JvmStatic
        fun setup() {
            digestService = DigestServiceMock()
        }
    }

    @Test
    fun `getSigningData should return hash of byte array for CustomSignatureSpec without alg spec`() {
        val spec = CustomSignatureSpec(
            signatureName = "NONEwithECDSA",
            customDigestName = DigestAlgorithmName.SHA2_256
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertArrayEquals(expected, spec.getSigningData(digestService, data))
    }

    @Test
    fun `getSigningData should return hash of byte array for CustomSignatureSpec with alg spec`() {
        val spec = CustomSignatureSpec(
            "RSA/NONE/PKCS1Padding",
            DigestAlgorithmName.SHA2_256,
            mock()
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val expected = MessageDigest.getInstance("SHA-256").digest(data)
        assertArrayEquals(expected, spec.getSigningData(digestService, data))
    }

    @Test
    fun `getSigningData should return original byte array for SignatureSpec`() {
        val spec = SignatureSpecs.ECDSA_SHA256
        val data = UUID.randomUUID().toString().toByteArray()
        assertArrayEquals(data, spec.getSigningData(digestService, data))
    }

    @Test
    fun `getSigningData should return original byte array for ParameterizedSignatureSpec`() {
        val spec = ParameterizedSignatureSpec(
            "RSASSA-PSS",
            PSSParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                32,
                1
            )
        )
        val data = UUID.randomUUID().toString().toByteArray()
        assertArrayEquals(data, spec.getSigningData(digestService, data))
    }

    class DigestServiceMock : PlatformDigestService {
        override fun hash(bytes: ByteArray, platformDigestName: DigestAlgorithmName): SecureHash =
            SecureHashImpl(platformDigestName.name, MessageDigest.getInstance(platformDigestName.name).digest(bytes))

        override fun hash(inputStream: InputStream, platformDigestName: DigestAlgorithmName): SecureHash {
            val messageDigest = MessageDigest.getInstance(platformDigestName.name)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while(true) {
                val read = inputStream.read(buffer)
                if(read <= 0) break
                messageDigest.update(buffer, 0, read)
            }
            return SecureHashImpl(platformDigestName.name, messageDigest.digest())
        }

        override fun parseSecureHash(algoNameAndHexString: String): SecureHash {
            TODO("Not yet implemented")
        }

        override fun digestLength(platformDigestName: DigestAlgorithmName): Int =
            MessageDigest.getInstance(platformDigestName.name).digestLength

        override fun defaultDigestAlgorithm(): DigestAlgorithmName {
            TODO("Not yet implemented")
        }

        override fun supportedDigestAlgorithms(): Set<DigestAlgorithmName> {
            TODO("Not yet implemented")
        }
    }
}