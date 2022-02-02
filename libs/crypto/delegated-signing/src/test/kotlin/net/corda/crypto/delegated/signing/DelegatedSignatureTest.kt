package net.corda.crypto.delegated.signing

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature

class DelegatedSignatureTest {
    private inner class SignatureTestProvider : Provider(
        SignatureTestProvider::class.java.canonicalName,
        "0.0",
        "Test signature provider"
    ) {
        init {
            putService(SignatureTestService(this, null))
            Hash.values().forEach {
                putService(SignatureTestService(this, it))
            }
        }
    }

    private inner class SignatureTestService(
        provider: Provider,
        private val defaultHash:
            Hash?
    ) : Provider.Service(
        provider,
        "Signature",
        "Test-$defaultHash",
        SignatureTestService::class.java.canonicalName,
        emptyList(),
        emptyMap()

    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedSignature(defaultHash)
        }
    }

    @BeforeEach
    fun setUp() {
        Security.addProvider(SignatureTestProvider())
    }
    @AfterEach
    fun cleanUp() {
        Security.removeProvider(SignatureTestProvider::class.java.canonicalName)
    }

    @Test
    fun `engineInitSign throws exception if private key is not DelegatedRsaPrivateKey`() {
        val signature = Signature.getInstance("test-null")
        val privateKey = mock<PrivateKey>()

        assertThrows<IllegalArgumentException> {

            signature.initSign(privateKey)
        }
    }

    @Test
    fun `engineSign returns data from service`() {
        val hash = Hash.SHA384
        val mockPublicKey = mock<PublicKey>()
        val data = "data".toByteArray()
        val sign = "sign".toByteArray()
        val signature = Signature.getInstance("test-$hash")
        val service = mock<DelegatedSigner> {
            on { sign(mockPublicKey, hash, data) } doReturn sign
        }
        val privateKey = mock<DelegatedPrivateKey> {
            on { publicKey } doReturn mockPublicKey
            on { signer } doReturn service
        }
        signature.initSign(privateKey)
        signature.update(data)

        Assertions.assertThat(signature.sign()).isEqualTo(sign)
    }

    @Test
    fun `engineSign without hash throws an exception`() {
        val data = "data".toByteArray()
        val signature = Signature.getInstance("test-null")
        val service = mock<DelegatedSigner>()
        val privateKey = mock<DelegatedPrivateKey> {
            on { signer } doReturn service
        }
        signature.initSign(privateKey)
        signature.update(data)

        assertThrows<SecurityException> {
            signature.sign()
        }
    }

    @Test
    fun `engineSetParameter set the correct hash`() {
        val hash = Hash.SHA384
        val mockPublicKey = mock<PublicKey>()
        val data = "data".toByteArray()
        val signature = Signature.getInstance("test-null")
        val service = mock<DelegatedSigner> {
            on { sign(mockPublicKey, hash, data) } doReturn ByteArray(0)
        }
        val privateKey = mock<DelegatedPrivateKey> {
            on { signer } doReturn service
            on { publicKey } doReturn mockPublicKey
        }

        signature.initSign(privateKey)
        signature.setParameter(hash.rsaParameter)
        data.forEach {
            signature.update(it)
        }
        signature.sign()

        verify(service).sign(mockPublicKey, hash, data)
    }

    @Test
    fun `engineSetParameter will ignore unknown parameters`() {
        val data = "data".toByteArray()
        val signature = Signature.getInstance("test-null")
        val service = mock<DelegatedSigner> {
            on { sign(any(), any(), any()) } doReturn ByteArray(0)
        }
        val privateKey = mock<DelegatedPrivateKey> {
            on { signer } doReturn service
        }

        signature.initSign(privateKey)
        signature.setParameter(mock())
        signature.update(data)

        assertThrows<SecurityException> {
            signature.sign()
        }
    }

    @Test
    fun `engineGetParameter will return null by default`() {
        val signature = Signature.getInstance("test-null")

        Assertions.assertThat(signature.parameters).isNull()
    }

    @Test
    fun `engineGetParameter will return valid parameter if set`() {
        val hash = Hash.SHA512
        val signature = Signature.getInstance("test-$hash")

        val parameters = signature.parameters

        Assertions.assertThat(parameters?.algorithm).isEqualTo("RSASSA-PSS")
    }

    @Test
    fun `engineSetParameter by name will throw an exception`() {
        val signature = Signature.getInstance("test-null")

        assertThrows<UnsupportedOperationException> {
            @Suppress("DEPRECATION")
            signature.setParameter("name", "value")
        }
    }

    @Test
    fun `engineGetParameter by name will throw an exception`() {
        val signature = Signature.getInstance("test-null")

        assertThrows<UnsupportedOperationException> {
            @Suppress("DEPRECATION")
            signature.getParameter("name")
        }
    }

    @Test
    fun `engineInitVerify will throws an exception`() {
        val hash = Hash.SHA512
        val signature = Signature.getInstance("test-$hash")

        assertThrows<UnsupportedOperationException> {
            signature.initVerify(mock<PublicKey>())
        }
    }
}
