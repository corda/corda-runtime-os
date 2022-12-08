package net.corda.membership.lib.impl.converter

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.layeredpropertymap.ConversionContext
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MemberNotaryDetailsConverterTest {
    private val publicKeyOne = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(1, 2, 3)
    }
    private val publicKeyTwo = mock<PublicKey> {
        on { encoded } doReturn byteArrayOf(4, 5)
    }
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey("PEM1") } doReturn publicKeyOne
        on { decodePublicKey("PEM2") } doReturn publicKeyTwo
    }
    private val hashOne = PublicKeyHash.calculate(publicKeyOne).value
    private val hashTwo = PublicKeyHash.calculate(publicKeyTwo).value
    private val context = mock<ConversionContext> {
        on { value("service.name") } doReturn "O=Service, L=London, C=GB"
        on { value("service.plugin") } doReturn "net.corda.membership.Plugin"
        on { value("keys.0.hash") } doReturn hashOne
        on { value("keys.0.pem") } doReturn "PEM1"
        on { value("keys.0.signature.spec") } doReturn SignatureSpec.ECDSA_SHA512.signatureName
        on { value("keys.1.hash") } doReturn hashTwo
        on { value("keys.1.pem") } doReturn "PEM2"
        on { value("keys.1.signature.spec") } doReturn SignatureSpec.RSA_SHA512.signatureName
    }

    private val converter = MemberNotaryDetailsConverter(keyEncodingService)

    @Test
    fun `convert return correct details when available`() {
        val notaryDetails = converter.convert(context)

        assertSoftly { softly ->
            softly.assertThat(notaryDetails.serviceName).isEqualTo(MemberX500Name.parse("O=Service, L=London, C=GB"))
            softly.assertThat(notaryDetails.servicePlugin).isEqualTo("net.corda.membership.Plugin")
            softly.assertThat(notaryDetails.keys.toList())
                .hasSize(2)
                .anySatisfy {
                    assertThat(it.publicKey).isEqualTo(publicKeyOne)
                }
                .anySatisfy {
                    assertThat(it.publicKey).isEqualTo(publicKeyTwo)
                }
                .anySatisfy {
                    assertThat(it.publicKeyHash.value).isEqualTo(hashOne)
                }
                .anySatisfy {
                    assertThat(it.publicKeyHash.value).isEqualTo(hashTwo)
                }
                .anySatisfy {
                    assertThat(it.spec.signatureName).isEqualTo(SignatureSpec.ECDSA_SHA512.signatureName)
                }
                .anySatisfy {
                    assertThat(it.spec.signatureName).isEqualTo(SignatureSpec.RSA_SHA512.signatureName)
                }
        }
    }

    @Test
    fun `convert throws exception if service name is missing`() {
        whenever(context.value("service.name")).doReturn(null)

        assertThrows<ValueNotFoundException> {
            converter.convert(context)
        }
    }

    @Test
    fun `convert return null plugin if plugin is not set`() {
        whenever(context.value("service.plugin")).doReturn(null)

        assertThat(converter.convert(context).servicePlugin).isNull()
    }

    @Test
    fun `convert ignore key if hash is missing`() {
        whenever(context.value("keys.1.hash")).doReturn(null)

        val notaryDetails = converter.convert(context)

        assertThat(notaryDetails.keys).hasSize(1)
    }

    @Test
    fun `convert ignore key if pem is missing`() {
        whenever(context.value("keys.1.pem")).doReturn(null)

        val notaryDetails = converter.convert(context)

        assertThat(notaryDetails.keys).hasSize(1)
    }

    @Test
    fun `convert ignore key if spec is missing`() {
        whenever(context.value("keys.1.signature.spec")).doReturn(null)

        val notaryDetails = converter.convert(context)

        assertThat(notaryDetails.keys).hasSize(1)
    }
}
