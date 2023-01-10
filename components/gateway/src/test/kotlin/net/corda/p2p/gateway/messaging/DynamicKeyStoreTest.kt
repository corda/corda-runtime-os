package net.corda.p2p.gateway.messaging

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.GatewayTlsCertificates
import net.corda.p2p.gateway.messaging.http.KeyStoreWithPassword
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture

class DynamicKeyStoreTest {
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val nodeConfiguration = mock<SmartConfig>()
    private val subscription = mock<CompactedSubscription<String, GatewayTlsCertificates>>()

    private val processorForKeystore = argumentCaptor<CompactedProcessor<String, GatewayTlsCertificates>>()
    private val subscriptionFactoryForKeystore = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processorForKeystore.capture(), eq(nodeConfiguration)) } doReturn subscription
    }

    private val certificateFactory = mock<CertificateFactory>()
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java)
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { mock, context ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, GatewayTlsCertificates>)).invoke()
    }
    private val keyStoreWithPassword = mock<KeyStoreWithPassword>()
    private val keyStoreFactory = mock<KeyStoreFactory> {
        on { createDelegatedKeyStore() } doReturn keyStoreWithPassword
    }

    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private var futures: MutableList<CompletableFuture<Unit>> = mutableListOf()
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        futures.add(context.arguments()[2] as CompletableFuture<Unit>)
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val id = HoldingIdentity(
        "name",
        "group"
    )

    private var keyStoreCreationSigner: DelegatedSigner? = null
    private var keyStoreCreationCertificatesStore: DelegatedCertificateStore? = null

    private val dynamicKeyStore = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactoryForKeystore,
        nodeConfiguration,
        cryptoOpsClient,
        certificateFactory
    ) { signer, certificatesStore ->
        keyStoreCreationSigner = signer
        keyStoreCreationCertificatesStore = certificatesStore
        keyStoreFactory
    }

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        blockingDominoTile.close()
    }

    @Nested
    inner class CreateResourcesTest {

        @Test
        fun `create resources will not complete without snapshots`() {
            futures.forEach {
                assertThat(it).isNotCompleted
            }
        }

        @Test
        fun `create resources will complete with snapshots`() {
            processorForKeystore.firstValue.onSnapshot(emptyMap())

            futures.forEach {
                assertThat(it).isCompleted
            }
        }
    }

    @Nested
    inner class ProcessorTest {
        private val certificates = (1..4).associate {
            "certificate$it" to mock<Certificate>()
        }

        @BeforeEach
        fun setUp() {
            whenever(certificateFactory.generateCertificate(any())).doAnswer {
                val inputStream = it.arguments[0] as InputStream
                val name = inputStream.reader().readText()
                certificates[name]
            }
        }

        @Test
        fun `onSnapshot save the correct data`() {
            processorForKeystore.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", id, certificates.keys.toList())
                )
            )

            assertThat(dynamicKeyStore.aliasToCertificates["one"]).containsAll(certificates.values)
        }

        @Test
        fun `onNext remove data with null value`() {
            processorForKeystore.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", id, certificates.keys.toList())
                )
            )

            processorForKeystore.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    null,
                ),
                null,
                emptyMap()
            )

            assertThat(dynamicKeyStore.aliasToCertificates["one"]).isNull()
        }

        @Test
        fun `onNext add data with valid value`() {
            processorForKeystore.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    GatewayTlsCertificates("id", id, certificates.keys.toList()),
                ),
                null,
                emptyMap()
            )

            assertThat(dynamicKeyStore.aliasToCertificates["one"]).containsAll(certificates.values)
        }
    }

    @Nested
    inner class DelegatedSignerTest {
        private val publicKeyOne = mock<PublicKey>()
        private val certificateOne = mock<Certificate> {
            on { publicKey } doReturn publicKeyOne
        }
        private val publicKeyTwo = mock<PublicKey>()
        private val certificateTwo = mock<Certificate> {
            on { publicKey } doReturn publicKeyTwo
        }
        private val certificateWithoutPublicKey = mock<Certificate> {
            on { publicKey } doReturn null
        }
        private val tenantIdOne = "idOne"
        private val tenantIdTwo = "idTwo"
        private val spec = mock<SignatureSpec>()
        private val data = "123".toByteArray()

        @BeforeEach
        fun setUp() {
            whenever(certificateFactory.generateCertificate(any())).doAnswer {
                val inputStream = it.arguments[0] as InputStream
                when (inputStream.reader().readText()) {
                    "1" -> certificateOne
                    "2" -> certificateTwo
                    else -> certificateWithoutPublicKey
                }
            }

            listOf(processorForKeystore.firstValue, processorForKeystore.firstValue).forEach {
                it.onSnapshot(
                    mapOf(
                        "one" to GatewayTlsCertificates(
                            tenantIdOne,
                            id,
                            listOf("1")
                        ),
                        "three" to GatewayTlsCertificates(
                            tenantIdOne,
                            id,
                            listOf("3")
                        ),
                    )
                )
            }
        }

        @Test
        fun `sign with unknown publicKey will throw an exception`() {
            assertThrows<InvalidKeyException> {
                dynamicKeyStore.sign(mock(), spec, data)
            }
        }

        @Test
        fun `when keystore is not using stubs, sign with known publicKey will send the correct data`() {
            val returnedData = "ok".toByteArray()
            val signatureWithKey = DigitalSignature.WithKey(publicKeyOne, returnedData, emptyMap())
            whenever(cryptoOpsClient.sign(anyString(), any(), any<SignatureSpec>(), any(), any()))
                .doReturn(signatureWithKey)
            dynamicKeyStore.sign(publicKeyOne, spec, data)

            verify(cryptoOpsClient).sign(tenantIdOne, publicKeyOne, spec, data)
        }

        @Test
        fun `when keystore is not using stubs, sign with known publicKey will return the correct data`() {
            val returnedData = "ok".toByteArray()
            val signatureWithKey = DigitalSignature.WithKey(publicKeyOne, returnedData, emptyMap())
            whenever(cryptoOpsClient.sign(anyString(), any(), any<SignatureSpec>(), any(), any()))
                .doReturn(signatureWithKey)

            assertThat(dynamicKeyStore.sign(publicKeyOne, spec, data)).isEqualTo(returnedData)
        }

        @Test
        fun `onNext will remove the public key`() {
            processorForKeystore.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    null,
                ),
                null,
                emptyMap()
            )

            assertThrows<InvalidKeyException> {
                dynamicKeyStore.sign(publicKeyOne, spec, data)
            }
        }

        @Test
        fun `onNext will replace the public key`() {
            val returnedData = "ok".toByteArray()
            val signatureWithKey = DigitalSignature.WithKey(publicKeyOne, returnedData, emptyMap())
            whenever(cryptoOpsClient.sign(anyString(), any(), any<SignatureSpec>(), any(), any()))
                .doReturn(signatureWithKey)
            processorForKeystore.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    GatewayTlsCertificates(
                        tenantIdTwo,
                        id,
                        listOf("2")
                    ),
                ),
                null,
                emptyMap()
            )

            assertDoesNotThrow {
                dynamicKeyStore.sign(publicKeyTwo, spec, data)
            }
        }
    }

    @Nested
    inner class KeyStoreTest {
        @Test
        fun `keyStore creates a new keystore`() {
            dynamicKeyStore.serverKeyStore

            verify(keyStoreFactory).createDelegatedKeyStore()
        }
    }

    @Nested
    inner class ClientKeyStoreTests {
        private val certificates = (1..4).associate {
            "certificate$it" to mock<PublicKey>()
        }.mapValues { (_, key) ->
            mock<Certificate> {
                on { publicKey } doReturn key
            }
        }

        @BeforeEach
        fun setUp() {
            whenever(certificateFactory.generateCertificate(any())).doAnswer {
                val inputStream = it.arguments[0] as InputStream
                val name = inputStream.reader().readText()
                certificates[name]
            }
            processorForKeystore.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    GatewayTlsCertificates("id", id, certificates.keys.toList()),
                ),
                null,
                emptyMap()
            )
        }


        @Test
        fun `getClientKeyStore returns null if certificates are unknown`() {
            assertThat(
                dynamicKeyStore.getClientKeyStore(
                    HoldingIdentity(
                        "another-name",
                        "group"
                    )
                )
            ).isNull()
        }

        @Test
        fun `getClientKeyStore returns the correct key store when the correct ID is used`() {
            assertThat(
                dynamicKeyStore.getClientKeyStore(
                    id,
                )
            ).isSameAs(keyStoreWithPassword)
        }

        @Test
        fun `getClientKeyStore uses the correct aliasToCertificates`() {
            dynamicKeyStore.getClientKeyStore(
                id
            )

            assertThat(keyStoreCreationCertificatesStore?.aliasToCertificates)
                .hasSize(1)
                .allSatisfy { tenantId, certs ->
                    assertThat(tenantId).isEqualTo("id")
                    assertThat(certs).containsExactlyInAnyOrderElementsOf(certificates.values)
                }
        }

        @Test
        fun `getClientKeyStore throw exception when wrong public key is used`() {
            dynamicKeyStore.getClientKeyStore(
                id
            )

            assertThrows<InvalidKeyException> {
                keyStoreCreationSigner?.sign(
                    mock(),
                    mock(),
                    "hello".toByteArray()
                )
            }
        }

        @Test
        fun `getClientKeyStore sign the data when the correct public key is used`() {
            val spec = mock<SignatureSpec>()
            val data = "hello".toByteArray()
            val returnedData = "ok".toByteArray()
            val publicKey = certificates["certificate1"]?.publicKey!!
            val signatureWithKey = DigitalSignature.WithKey(publicKey, returnedData, emptyMap())
            whenever(
                cryptoOpsClient.sign(
                    "id",
                    publicKey,
                    spec,
                    data,
                    emptyMap(),
                )
            ).doReturn(signatureWithKey)

            dynamicKeyStore.getClientKeyStore(
                id
            )

            assertThat(
                keyStoreCreationSigner?.sign(
                    publicKey,
                    spec,
                    data,
                )
            ).isEqualTo(returnedData)
        }

        @Test
        fun `onNext will remove the client keystore if the value is null`() {
            processorForKeystore.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    null,
                ),
                GatewayTlsCertificates("id", id, certificates.keys.toList()),
                emptyMap()
            )

            assertThat(
                dynamicKeyStore.getClientKeyStore(
                    id,
                )
            ).isNull()
        }
    }
}
