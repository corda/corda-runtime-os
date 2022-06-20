package net.corda.p2p.gateway.messaging

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.impl.CryptoOpsClientComponent
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
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
import org.mockito.kotlin.never
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

    private val processorForKeystoreWithStubs = argumentCaptor<CompactedProcessor<String, GatewayTlsCertificates>>()
    private val subscriptionFactoryForKeystoreWithStubs = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processorForKeystoreWithStubs.capture(), eq(nodeConfiguration)) } doReturn subscription
    }

    private val processorForKeystoreWithoutStubs = argumentCaptor<CompactedProcessor<String, GatewayTlsCertificates>>()
    private val subscriptionFactoryForKeystoreWithoutStubs = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processorForKeystoreWithoutStubs.capture(), eq(nodeConfiguration)) } doReturn subscription
    }

    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn mock()
    }
    private val configurationReaderService = mock<ConfigurationReadService>()
    private val certificateFactory = mock<CertificateFactory>()
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java)
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java) { mock, _ ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val signer = mockConstruction(StubCryptoProcessor::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }
    private val cryptoOpsClient = mockConstruction(CryptoOpsClientComponent::class.java)
    private var futures: MutableList<CompletableFuture<Unit>> = mutableListOf()
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        futures.add(context.arguments()[2] as CompletableFuture<Unit>)
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }

    private val dynamicKeyStoreWithStubs = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactoryForKeystoreWithStubs,
        publisherFactory,
        nodeConfiguration,
        configurationReaderService,
        SigningMode.STUB,
        certificateFactory,
    )

    private val dynamicKeystoreWithoutStubs = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactoryForKeystoreWithoutStubs,
        publisherFactory,
        nodeConfiguration,
        configurationReaderService,
        SigningMode.REAL,
        certificateFactory
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        signer.close()
        cryptoOpsClient.close()
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
            processorForKeystoreWithStubs.firstValue.onSnapshot(emptyMap())
            processorForKeystoreWithoutStubs.firstValue.onSnapshot(emptyMap())

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
            processorForKeystoreWithStubs.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", certificates.keys.toList())
                )
            )

            assertThat(dynamicKeyStoreWithStubs.aliasToCertificates["one"]).containsAll(certificates.values)
        }

        @Test
        fun `onNext remove data with null value`() {
            processorForKeystoreWithStubs.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", certificates.keys.toList())
                )
            )

            processorForKeystoreWithStubs.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    null,
                ),
                null,
                emptyMap()
            )

            assertThat(dynamicKeyStoreWithStubs.aliasToCertificates["one"]).isNull()
        }

        @Test
        fun `onNext add data with valid value`() {
            processorForKeystoreWithStubs.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    GatewayTlsCertificates("id", certificates.keys.toList()),
                ),
                null,
                emptyMap()
            )

            assertThat(dynamicKeyStoreWithStubs.aliasToCertificates["one"]).containsAll(certificates.values)
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

            listOf(processorForKeystoreWithStubs.firstValue, processorForKeystoreWithoutStubs.firstValue).forEach {
                it.onSnapshot(
                    mapOf(
                        "one" to GatewayTlsCertificates(
                            tenantIdOne,
                            listOf("1")
                        ),
                        "three" to GatewayTlsCertificates(
                            tenantIdOne,
                            listOf("3")
                        ),
                    )
                )
            }
        }

        @Test
        fun `sign with unknown publicKey will throw an exception`() {
            assertThrows<InvalidKeyException> {
                dynamicKeyStoreWithStubs.sign(mock(), spec, data)
            }
        }

        @Test
        fun `when keystore is using stubs, sign with known publicKey will send the correct data`() {
            dynamicKeyStoreWithStubs.sign(publicKeyOne, spec, data)

            verify(signer.constructed().first()).sign(tenantIdOne, publicKeyOne, spec, data)
            verify(cryptoOpsClient.constructed().first(), never()).sign(anyString(), any(), any<SignatureSpec>(), any(), any())
        }

        @Test
        fun `when keystore is using stubs, sign with known publicKey will return the correct data`() {
            val returnedData = "ok".toByteArray()
            whenever(signer.constructed().first().sign(any(), any(), any(), any())).doReturn(returnedData)

            assertThat(dynamicKeyStoreWithStubs.sign(publicKeyOne, spec, data)).isEqualTo(returnedData)
        }

        @Test
        fun `when keystore is not using stubs, sign with known publicKey will send the correct data`() {
            val returnedData = "ok".toByteArray()
            val signatureWithKey = DigitalSignature.WithKey(publicKeyOne, returnedData, emptyMap())
            whenever(cryptoOpsClient.constructed().first().sign(anyString(), any(), any<SignatureSpec>(), any(), any()))
                .doReturn(signatureWithKey)
            dynamicKeystoreWithoutStubs.sign(publicKeyOne, spec, data)

            verify(cryptoOpsClient.constructed().first()).sign(tenantIdOne, publicKeyOne, spec, data)
            verify(signer.constructed().first(), never()).sign(any(), any(), any(), any())
        }

        @Test
        fun `when keystore is not using stubs, sign with known publicKey will return the correct data`() {
            val returnedData = "ok".toByteArray()
            val signatureWithKey = DigitalSignature.WithKey(publicKeyOne, returnedData, emptyMap())
            whenever(cryptoOpsClient.constructed().first().sign(anyString(), any(), any<SignatureSpec>(), any(), any()))
                .doReturn(signatureWithKey)

            assertThat(dynamicKeystoreWithoutStubs.sign(publicKeyOne, spec, data)).isEqualTo(returnedData)
            verify(signer.constructed().first(), never()).sign(any(), any(), any(), any())
        }

        @Test
        fun `onNext will remove the public key`() {
            processorForKeystoreWithStubs.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    null,
                ),
                null,
                emptyMap()
            )

            assertThrows<InvalidKeyException> {
                dynamicKeyStoreWithStubs.sign(publicKeyOne, spec, data)
            }
        }

        @Test
        fun `onNext will replace the public key`() {
            processorForKeystoreWithStubs.firstValue.onNext(
                Record(
                    GATEWAY_TLS_CERTIFICATES,
                    "one",
                    GatewayTlsCertificates(
                        tenantIdTwo,
                        listOf("2")
                    ),
                ),
                null,
                emptyMap()
            )

            assertDoesNotThrow {
                dynamicKeyStoreWithStubs.sign(publicKeyTwo, spec, data)
            }
        }
    }

    @Nested
    inner class KeyStoreTest {
        @Test
        fun `keyStore creates a new keystore`() {
            mockConstruction(KeyStoreFactory::class.java).use {
                dynamicKeyStoreWithStubs.keyStore

                verify(it.constructed().first()).createDelegatedKeyStore()
            }
        }
    }
}
