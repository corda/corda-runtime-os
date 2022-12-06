package net.corda.membership.impl.registration

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.nio.ByteBuffer
import java.security.PublicKey

class KeysFactoryTest {
    private val tenantId = "tenantId"
    private val scheme = "scheme"
    private val exitedCategory = "category-one"
    private val missingCategory = "category-two"
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
                    this[CATEGORY_FILTER] == exitedCategory &&
                        this.containsKey(ALIAS_FILTER)
                },
            )
        } doReturn listOf(cryptoSigningKey)
        on {
            lookup(
                eq(tenantId),
                eq(0),
                any(),
                any(),
                argThat {
                    this[CATEGORY_FILTER] == missingCategory &&
                        this.containsKey(ALIAS_FILTER)
                },
            )
        } doReturn emptyList()
        on {
            generateKeyPair(
                tenantId = eq(tenantId),
                category = eq(missingCategory),
                alias = argThat {
                    this.contains(missingCategory)
                },
                scheme = eq(scheme),
                context = any(),
            )
        } doReturn publicKey
        on {
            lookup(tenantId, listOf(publicKey.publicKeyId()))
        } doReturn listOf(cryptoSigningKey)
    }

    private val keysFactory = KeysFactory(
        cryptoOpsClient,
        keyEncodingService,
        scheme,
        tenantId,
    )

    @Test
    fun `if the key exists, new key will not be generated`() {
        keysFactory.getOrGenerateKeyPair(exitedCategory)

        verify(cryptoOpsClient, never()).generateKeyPair(
            any(),
            any(),
            any(),
            any(),
            any<Map<String, String>>(),
        )
    }

    @Test
    fun `if the key is missing, new key will be generated`() {
        keysFactory.getOrGenerateKeyPair(missingCategory)

        verify(cryptoOpsClient).generateKeyPair(
            any(),
            category = eq(missingCategory),
            any(),
            any(),
            any<Map<String, String>>(),
        )
    }

    @Test
    fun `pem returns the correct PEM`() {
        val key = keysFactory.getOrGenerateKeyPair(exitedCategory)

        assertThat(key.pem).isEqualTo("PEM")
    }

    @Test
    fun `hash returns the correct hash`() {
        val key = keysFactory.getOrGenerateKeyPair(missingCategory)

        assertThat(key.hash).isEqualTo(publicKey.calculateHash())
    }

    @Test
    fun `spec returns the correct signature spec`() {
        val key = keysFactory.getOrGenerateKeyPair(exitedCategory)

        assertThat(key.spec).isEqualTo(SignatureSpec.ECDSA_SHA256)
    }
}
