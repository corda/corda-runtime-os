package net.corda.cipher.suite.impl

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SignatureSpec
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
                publicKey = publicKey,
                digest = DigestAlgorithmName.SHA2_256,
                signatureData = UUID.randomUUID().toString().toByteArray(),
                clearData = UUID.randomUUID().toString().toByteArray()
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
                publicKey = publicKey,
                signatureSpec = SignatureSpec.ECDSA_SHA256,
                signatureData = UUID.randomUUID().toString().toByteArray(),
                clearData = UUID.randomUUID().toString().toByteArray()
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
                publicKey = publicKey,
                signatureSpec = SignatureSpec.ECDSA_SHA256,
                signatureData = ByteArray(0),
                clearData = UUID.randomUUID().toString().toByteArray()
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
                publicKey = publicKey,
                signatureSpec = SignatureSpec.ECDSA_SHA256,
                signatureData = UUID.randomUUID().toString().toByteArray(),
                clearData = ByteArray(0)
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
                publicKey = publicKey,
                digest = DigestAlgorithmName.SHA2_256,
                signatureData = UUID.randomUUID().toString().toByteArray(),
                clearData = UUID.randomUUID().toString().toByteArray()
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
                publicKey = publicKey,
                signatureSpec = SignatureSpec.ECDSA_SHA256,
                signatureData = UUID.randomUUID().toString().toByteArray(),
                clearData = UUID.randomUUID().toString().toByteArray()
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
                publicKey = publicKey,
                signatureSpec = SignatureSpec.ECDSA_SHA256,
                signatureData = ByteArray(0),
                clearData = UUID.randomUUID().toString().toByteArray()
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
                publicKey = publicKey,
                signatureSpec = SignatureSpec.ECDSA_SHA256,
                signatureData = UUID.randomUUID().toString().toByteArray(),
                clearData = ByteArray(0)
            )
        }
    }
}