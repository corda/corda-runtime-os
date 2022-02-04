package net.corda.crypto.delegated.signing

import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.security.AlgorithmParameters
import java.security.AlgorithmParametersSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec
import kotlin.math.sign

class DelegatedSignatureTest {
    companion object {
        private const val name = "Test-name"
    }
    private inner class SignatureTestProvider : Provider(
        SignatureTestProvider::class.java.canonicalName,
        "0.0",
        "Test signature provider"
    ) {
        init {
            putService(SignatureTestService(this))
            putService(AlgorithmParametersService(this))
        }
    }

    private inner class SignatureTestService(
        provider: Provider,
    ) : Provider.Service(
        provider,
        "Signature",
        name,
        SignatureTestService::class.java.canonicalName,
        emptyList(),
        emptyMap()

    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return DelegatedSignature(algorithm)
        }
    }
    private val algorithmParameters = object : AlgorithmParametersSpi() {
        var parameters: AlgorithmParameterSpec? = null
        override fun engineInit(paramSpec: AlgorithmParameterSpec?) {
            parameters = paramSpec
        }

        override fun engineInit(params: ByteArray?) {
        }

        override fun engineInit(params: ByteArray?, format: String?) {
        }

        override fun <T : AlgorithmParameterSpec> engineGetParameterSpec(paramSpec: Class<T>?): T? {
            return null
        }

        override fun engineGetEncoded(): ByteArray? {
            return null
        }

        override fun engineGetEncoded(format: String?): ByteArray? {
            return null
        }

        override fun engineToString() = "parameter name"

        override fun toString(): String {
            return engineToString()
        }
    }
    private inner class AlgorithmParametersService(
        provider: Provider
    ) : Provider.Service(
        provider,
        "AlgorithmParameters",
        name,
        AlgorithmParameters::class.java.canonicalName,
        emptyList(),
        emptyMap()
    ) {
        override fun newInstance(constructorParameter: Any?): Any {
            return algorithmParameters
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
        val signature = Signature.getInstance(name)
        val privateKey = mock<PrivateKey>()

        assertThrows<IllegalArgumentException> {

            signature.initSign(privateKey)
        }
    }

    @Test
    fun `engineSign returns data from service`() {
        val mockPublicKey = mock<PublicKey>()
        val data = "data".toByteArray()
        val sign = "sign".toByteArray()
        val signature = Signature.getInstance(name)
        val service = mock<DelegatedSigner> {
            on { sign(eq(mockPublicKey), any(), eq(data)) } doReturn sign
        }
        val privateKey = mock<DelegatedPrivateKey> {
            on { publicKey } doReturn mockPublicKey
            on { signer } doReturn service
        }
        signature.initSign(privateKey)
        signature.update(data)

        assertThat(signature.sign()).isEqualTo(sign)
    }

    @Test
    fun `engineSetParameter set the correct parameter`() {
        val mockPublicKey = mock<PublicKey>()
        val data = "data".toByteArray()
        val signature = Signature.getInstance(name)
        val spec = argumentCaptor<SignatureSpec>()
        val service = mock<DelegatedSigner> {
            on { sign(eq(mockPublicKey), spec.capture(), eq(data)) } doReturn ByteArray(0)
        }
        val privateKey = mock<DelegatedPrivateKey> {
            on { signer } doReturn service
            on { publicKey } doReturn mockPublicKey
        }
        val parameter = mock<AlgorithmParameterSpec>()

        signature.initSign(privateKey)
        signature.setParameter(parameter)
        data.forEach {
            signature.update(it)
        }
        signature.sign()

        assertThat(spec.firstValue.params).isEqualTo(parameter)
    }

    @Test
    fun `engineSign set the correct name`() {
        val mockPublicKey = mock<PublicKey>()
        val data = "data".toByteArray()
        val signature = Signature.getInstance(name)
        val spec = argumentCaptor<SignatureSpec>()
        val service = mock<DelegatedSigner> {
            on { sign(eq(mockPublicKey), spec.capture(), eq(data)) } doReturn ByteArray(0)
        }
        val privateKey = mock<DelegatedPrivateKey> {
            on { signer } doReturn service
            on { publicKey } doReturn mockPublicKey
        }

        signature.initSign(privateKey)
        data.forEach {
            signature.update(it)
        }
        signature.sign()

        assertThat(spec.firstValue.signatureName).isEqualTo(name)
    }

    @Test
    fun `engineGetParameter will return null by default`() {
        val signature = Signature.getInstance(name)

        assertThat(signature.parameters).isNull()
    }

    @Test
    fun `engineGetParameter will return valid parameter if set`() {
        val signature = Signature.getInstance(name)
        signature.setParameter(mock())

        val parameters = signature.parameters

        assertThat(parameters.toString()).isEqualTo(algorithmParameters.toString())
    }

    @Test
    fun `engineGetParameter will initilize the parameters`() {
        val signature = Signature.getInstance(name)
        val parameters = mock<AlgorithmParameterSpec>()
        signature.setParameter(parameters)

        signature.parameters

        assertThat(algorithmParameters.parameters).isEqualTo(parameters)
    }

    @Test
    fun `engineSetParameter by name will throw an exception`() {
        val signature = Signature.getInstance(name)

        assertThrows<UnsupportedOperationException> {
            @Suppress("DEPRECATION")
            signature.setParameter("name", "value")
        }
    }

    @Test
    fun `engineGetParameter by name will throw an exception`() {
        val signature = Signature.getInstance(name)

        assertThrows<UnsupportedOperationException> {
            @Suppress("DEPRECATION")
            signature.getParameter("name")
        }
    }

    @Test
    fun `engineInitVerify will throws an exception`() {
        val signature = Signature.getInstance(name)

        assertThrows<UnsupportedOperationException> {
            signature.initVerify(mock<PublicKey>())
        }
    }
}
