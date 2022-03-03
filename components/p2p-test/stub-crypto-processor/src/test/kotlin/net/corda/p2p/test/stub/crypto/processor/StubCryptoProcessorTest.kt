package net.corda.p2p.test.stub.crypto.processor

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.test.KeyAlgorithm
import net.corda.p2p.test.KeyPairEntry
import net.corda.p2p.test.TenantKeys
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec
import java.util.concurrent.CompletableFuture

class StubCryptoProcessorTest {
    private val processor = argumentCaptor<CompactedProcessor<String, TenantKeys>>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val subscription = mock< CompactedSubscription<String, TenantKeys>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), any()) } doReturn subscription
    }
    private val configuration = mock<SmartConfig>()
    private val privateKey = mock<PrivateKey>()
    private val rsaPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "RSA"
    }
    private val ecPublicKey = mock<PublicKey> {
        on { algorithm } doReturn "EC"
    }
    private val keyDeserialiser = mockConstruction(KeyDeserialiser::class.java) { mock, _ ->
        whenever(mock.toPrivateKey(any(), any())).doReturn(privateKey)
        whenever(mock.toPublicKey(any(), eq(KeyAlgorithm.RSA))).doReturn(rsaPublicKey)
        whenever(mock.toPublicKey(any(), eq(KeyAlgorithm.ECDSA))).doReturn(ecPublicKey)
    }
    private var createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)? = null
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((resources: ResourcesHolder) -> CompletableFuture<Unit>)
    }
    private val algorithmParameterSpec = mock<AlgorithmParameterSpec>()
    private val spec = SignatureSpec("signature-name", params = algorithmParameterSpec)
    private val data = "data".toByteArray()
    private val rsaSignature = mock<Signature> {
        on { sign() } doReturn "RSA-Signature".toByteArray()
    }
    private val ecSignature = mock<Signature> {
        on { sign() } doReturn "EC-Signature".toByteArray()
    }
    private val mockSignature = mockStatic(Signature::class.java).also {
        it.`when`<Signature> {
            Signature.getInstance(any(), eq("SunRsaSign"))
        }.doReturn(rsaSignature)
        it.`when`<Signature> {
            Signature.getInstance(any(), eq("SunEC"))
        }.doReturn(ecSignature)
    }
    private val mockSubscriptionTile = mockConstruction(SubscriptionDominoTile::class.java)
    private val tenantId = "tenantId"

    private val testObject = StubCryptoProcessor(lifecycleCoordinatorFactory, subscriptionFactory, 100, configuration)

    @AfterEach
    fun cleanUp() {
        mockSubscriptionTile.close()
        mockSignature.close()
        keyDeserialiser.close()
        dominoTile.close()
    }
    @Nested
    inner class Sign {
        @BeforeEach
        fun setUp() {
            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "key1",
                    TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.RSA,
                            ByteBuffer.wrap(data), ByteBuffer.wrap(data)
                        )
                    )
                ),
                null, emptyMap()
            )
            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "key1",
                    TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.ECDSA,
                            ByteBuffer.wrap(data), ByteBuffer.wrap(data)
                        )
                    )
                ),
                null, emptyMap()
            )
        }

        @Test
        fun `sign throws exception for unknown key`() {
            assertThrows<CouldNotFindPrivateKey> {
                testObject.sign(tenantId, mock(), spec, data)
            }
        }

        @Test
        fun `sign throws exception for unknown key algorithm`() {
            val key = mock<PublicKey> {
                on { algorithm } doReturn "NOP"
            }
            whenever(keyDeserialiser.constructed().first().toPublicKey(any(), any())).doReturn(key)
            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "key1",
                    TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.ECDSA,
                            ByteBuffer.wrap(data), ByteBuffer.wrap(data)
                        ),
                    ),
                ),
                null, emptyMap()
            )

            assertThrows<UnsupportedAlgorithm> {
                testObject.sign(tenantId, key, spec, data)
            }
        }

        @Test
        fun `sign send the correct parameters`() {
            testObject.sign(tenantId, rsaPublicKey, spec, data)

            verify(rsaSignature).initSign(privateKey)
            verify(rsaSignature).setParameter(algorithmParameterSpec)
            verify(rsaSignature).update(data)
        }

        @Test
        fun `sign returns the correct parameters`() {
            val signature = testObject.sign(tenantId, ecPublicKey, spec, data)

            assertThat(signature).isEqualTo("EC-Signature".toByteArray())
        }

        @Test
        fun `sign throws exception for unknown tenantID`() {
            assertThrows<CouldNotFindPrivateKey> {
                testObject.sign("$tenantId-1", ecPublicKey, spec, data)
            }
        }
    }

    @Nested
    inner class CreateResourcesTests {
        private val resourcesHolder = ResourcesHolder()
        @Test
        fun `createResources will not complete without snapshots`() {
            val future = createResources?.invoke(resourcesHolder)

            assertThat(future).isNotCompleted
        }

        @Test
        fun `createResources will complete with snapshots`() {
            val future = createResources?.invoke(resourcesHolder)

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(future).isCompleted
        }
    }

    @Nested
    inner class KeyPairEntryProcessorTests {
        @Test
        fun `onSnapshot adds data`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "RSA" to TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.RSA,
                            ByteBuffer.wrap(data), ByteBuffer.wrap(data)
                        )
                    )
                )
            )

            testObject.sign(tenantId, rsaPublicKey, spec, data)
            verify(rsaSignature).initSign(privateKey)
        }

        @Test
        fun `onSnapshot deserialize the keys`() {
            val privateData = "one".toByteArray()
            val publicData = "two".toByteArray()
            processor.firstValue.onSnapshot(
                mapOf(
                    "EC" to
                        TenantKeys(
                            tenantId,
                            KeyPairEntry(
                                KeyAlgorithm.ECDSA,
                                ByteBuffer.wrap(publicData),
                                ByteBuffer.wrap(privateData),
                            ),
                        ),
                )
            )

            verify(keyDeserialiser.constructed().first()).toPrivateKey(privateData, KeyAlgorithm.ECDSA)
            verify(keyDeserialiser.constructed().first()).toPublicKey(publicData, KeyAlgorithm.ECDSA)
        }

        @Test
        fun `onNext remove data if value is null`() {
            val oldPair = TenantKeys(
                tenantId,
                KeyPairEntry(
                    KeyAlgorithm.RSA,
                    ByteBuffer.wrap(data), ByteBuffer.wrap(data)
                )
            )
            processor.firstValue.onSnapshot(
                mapOf(
                    "RSA" to oldPair
                )
            )

            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "RSA",
                    null,
                ),
                oldPair,
                emptyMap()
            )

            assertThrows<CouldNotFindPrivateKey> {
                testObject.sign(tenantId, rsaPublicKey, spec, data)
            }
        }

        @Test
        fun `onNext with null values deserialize the public key (and only the public key)`() {
            val privateData = "one".toByteArray()
            val publicData = "two".toByteArray()
            processor.firstValue.onSnapshot(
                mapOf(
                    "RSA" to
                        TenantKeys(
                            tenantId,
                            KeyPairEntry(
                                KeyAlgorithm.RSA,
                                ByteBuffer.wrap(publicData),
                                ByteBuffer.wrap(privateData),
                            ),
                        ),
                )
            )

            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "RSA",
                    null,
                ),
                TenantKeys(
                    tenantId,
                    KeyPairEntry(
                        KeyAlgorithm.ECDSA,
                        ByteBuffer.wrap(publicData),
                        ByteBuffer.wrap(privateData),
                    ),
                ),
                emptyMap(),
            )

            verify(keyDeserialiser.constructed().first(), never()).toPrivateKey(privateData, KeyAlgorithm.ECDSA)
            verify(keyDeserialiser.constructed().first()).toPublicKey(publicData, KeyAlgorithm.ECDSA)
        }

        @Test
        fun `onNext with new value value will replace the private key`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "RSA" to TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.RSA,
                            ByteBuffer.wrap(data), ByteBuffer.wrap(data)
                        ),
                    )
                )
            )
            val newPrivateKeyData = "three".toByteArray()
            val newPrivateKey = mock<PrivateKey>()
            whenever(keyDeserialiser.constructed().first().toPrivateKey(newPrivateKeyData, KeyAlgorithm.RSA)).doReturn(newPrivateKey)

            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "RSA",
                    TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.RSA,
                            ByteBuffer.wrap(data),
                            ByteBuffer.wrap(newPrivateKeyData),
                        ),
                    )
                ),
                null,
                emptyMap(),
            )

            testObject.sign(tenantId, rsaPublicKey, spec, data)
            verify(rsaSignature).initSign(newPrivateKey)
        }

        @Test
        fun `onNext with new values deserialize the keys`() {
            val privateData = "one".toByteArray()
            val publicData = "two".toByteArray()

            processor.firstValue.onNext(
                Record(
                    CRYPTO_KEYS_TOPIC,
                    "RSA",
                    TenantKeys(
                        tenantId,
                        KeyPairEntry(
                            KeyAlgorithm.ECDSA,
                            ByteBuffer.wrap(publicData),
                            ByteBuffer.wrap(privateData),
                        ),
                    )
                ),
                null,
                emptyMap(),
            )

            verify(keyDeserialiser.constructed().first()).toPrivateKey(privateData, KeyAlgorithm.ECDSA)
            verify(keyDeserialiser.constructed().first()).toPublicKey(publicData, KeyAlgorithm.ECDSA)
        }
    }
}
