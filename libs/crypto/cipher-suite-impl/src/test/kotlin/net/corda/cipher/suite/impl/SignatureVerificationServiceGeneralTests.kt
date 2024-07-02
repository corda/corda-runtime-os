package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.utilities.toByteArray
import net.corda.v5.crypto.DigestAlgorithmName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.util.UUID

class SignatureVerificationServiceGeneralTests {
    companion object {
        private val publicKey = mock<PublicKey> {
            on { encoded } doReturn "1234".toByteArray()
        }
    }

    @Test
    fun `verify should throw IllegalArgumentException when in cannot infer signature spec`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { inferSignatureSpec(any(), any()) } doReturn null
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.verify(
                originalData = UUID.randomUUID().toByteArray(),
                signatureData = UUID.randomUUID().toByteArray(),
                publicKey = publicKey,
                digest = DigestAlgorithmName.SHA2_256
            )
        }
    }

    @Test
    fun `verify should throw IllegalArgumentException when key scheme is not supported`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { schemes } doReturn emptyList()
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.verify(
                originalData = UUID.randomUUID().toByteArray(),
                signatureData = UUID.randomUUID().toByteArray(),
                publicKey = publicKey,
                signatureSpec = SignatureSpecs.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `verify should throw IllegalArgumentException when key signature data is empty`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { schemes } doReturn listOf(ECDSA_SECP256R1_TEMPLATE.makeScheme("BC"))
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.verify(
                originalData = UUID.randomUUID().toByteArray(),
                signatureData = ByteArray(0),
                publicKey = publicKey,
                signatureSpec = SignatureSpecs.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `verify should throw IllegalArgumentException when key clear data is empty`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { schemes } doReturn listOf(ECDSA_SECP256R1_TEMPLATE.makeScheme("BC"))
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.verify(
                originalData = ByteArray(0),
                signatureData = UUID.randomUUID().toByteArray(),
                publicKey = publicKey,
                signatureSpec = SignatureSpecs.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `isValid should throw IllegalArgumentException when in cannot infer signature spec`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { inferSignatureSpec(any(), any()) } doReturn null
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.isValid(
                originalData = UUID.randomUUID().toByteArray(),
                signatureData = UUID.randomUUID().toByteArray(),
                publicKey = publicKey,
                digest = DigestAlgorithmName.SHA2_256
            )
        }
    }
    @Test
    fun `isValid should throw IllegalArgumentException when key scheme is not supported`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { schemes } doReturn emptyList()
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.isValid(
                originalData = UUID.randomUUID().toByteArray(),
                signatureData = UUID.randomUUID().toByteArray(),
                publicKey = publicKey,
                signatureSpec = SignatureSpecs.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `isValid should throw IllegalArgumentException when key signature data is empty`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { schemes } doReturn listOf(ECDSA_SECP256R1_TEMPLATE.makeScheme("BC"))
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.isValid(
                originalData = UUID.randomUUID().toByteArray(),
                signatureData = ByteArray(0),
                publicKey = publicKey,
                signatureSpec = SignatureSpecs.ECDSA_SHA256
            )
        }
    }

    @Test
    fun `isValid should throw IllegalArgumentException when key clear data is empty`() {
        val schemeMetadata = mock<CipherSchemeMetadata> {
            on { schemes } doReturn listOf(ECDSA_SECP256R1_TEMPLATE.makeScheme("BC"))
            on { findKeyScheme(publicKey)} doReturn ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        }
        val service = SignatureVerificationServiceImpl(
            schemeMetadata = schemeMetadata,
            hashingService = mock()
        )
        assertThrows<IllegalArgumentException> {
            service.isValid(
                originalData = ByteArray(0),
                signatureData = UUID.randomUUID().toByteArray(),
                publicKey = publicKey,
                signatureSpec = SignatureSpecs.ECDSA_SHA256
            )
        }
    }
}