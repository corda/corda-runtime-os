package net.corda.membership.impl.registration

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.calculateHash
import net.corda.v5.crypto.publicKeyId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.nio.ByteBuffer
import java.security.PublicKey

class KeysFactoryTest {
    private val tenantId = "tenantId"
    private val scheme = "scheme"
    private val noExistingKeyCategory = "category-one"
    private val existingKeyCategory = "category-two"
    private val encoded = byteArrayOf(33, 1)
    private val publicKey = mock<PublicKey> {
        on { encoded } doReturn encoded
    }
    private val keyEncodingService = mock<KeyEncodingService> {
        on { decodePublicKey(encoded) } doReturn publicKey
        on { encodeAsString(publicKey) } doReturn "PEM"
    }
    private val cryptoSigningKey = mock<CryptoSigningKey>() {
        on { publicKey } doReturn ByteBuffer.wrap(encoded)
        on { schemeCodeName } doReturn ECDSA_SECP256R1_CODE_NAME
    }
    private val cryptoOpsClient = mock<CryptoOpsClient> {
        on {
            lookup(
                eq(tenantId),
                eq(0),
                any(),
                any(),
                argThat {
                    this[CATEGORY_FILTER] == existingKeyCategory &&
                        this.containsKey(ALIAS_FILTER)
                },
            )
        } doReturn listOf(cryptoSigningKey)
        on {
            generateKeyPair(
                tenantId = eq(tenantId),
                category = eq(noExistingKeyCategory),
                alias = argThat {
                    this.contains(noExistingKeyCategory)
                },
                scheme = eq(scheme),
                context = any(),
            )
        } doReturn publicKey
        on {
            generateKeyPair(
                tenantId = eq(tenantId),
                category = eq(existingKeyCategory),
                alias = argThat {
                    this.contains(existingKeyCategory)
                },
                scheme = eq(scheme),
                context = any(),
            )
        } doThrow KeyAlreadyExistsException("", "", "")
        on {
            lookupKeysByIds(tenantId, listOf(ShortHash.of(publicKey.publicKeyId())))
        } doReturn listOf(cryptoSigningKey)
    }

    private val keysFactory = KeysFactory(
        cryptoOpsClient,
        keyEncodingService,
        scheme,
        tenantId,
    )

    @Test
    fun `new key will be generated, and lookup is not performed if the key doesn't already exist for alias and category`() {
        keysFactory.getOrGenerateKeyPair(noExistingKeyCategory)

        verify(cryptoOpsClient).generateKeyPair(
            any(),
            any(),
            any(),
            any(),
            any<Map<String, String>>(),
        )
        verify(cryptoOpsClient, never()).lookup(
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `if the key is exists already, a lookup is performed to get that key`() {
        keysFactory.getOrGenerateKeyPair(existingKeyCategory)

        verify(cryptoOpsClient).generateKeyPair(
            any(),
            category = eq(existingKeyCategory),
            any(),
            any(),
            any<Map<String, String>>(),
        )
        verify(cryptoOpsClient).lookup(
            any(),
            any(),
            any(),
            any(),
            any()
        )
    }

    @Test
    fun `pem returns the correct PEM`() {
        assertThat(
            keysFactory
                .getOrGenerateKeyPair(noExistingKeyCategory)
                .pem
        ).isEqualTo("PEM")
    }

    @Test
    fun `hash returns the correct hash`() {
        assertThat(
            keysFactory
                .getOrGenerateKeyPair(existingKeyCategory)
                .hash
        ).isEqualTo(publicKey.calculateHash())
    }

    @Test
    fun `spec returns the correct signature spec`() {
        assertThat(
            keysFactory
                .getOrGenerateKeyPair(noExistingKeyCategory)
                .spec
        ).isEqualTo(SignatureSpec.ECDSA_SHA256)
    }
}
