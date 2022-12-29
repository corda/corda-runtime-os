package net.corda.p2p.gateway.messaging

import net.corda.crypto.client.CryptoOpsClient
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
import net.corda.p2p.GatewayTlsCertificates
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

    private val cryptoOpsClient = mock<CryptoOpsClient>()
    private var futures: MutableList<CompletableFuture<Unit>> = mutableListOf()
    private val blockingDominoTile = mockConstruction(BlockingDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        futures.add(context.arguments()[2] as CompletableFuture<Unit>)
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }

    private val dynamicKeyStore = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactoryForKeystore,
        nodeConfiguration,
        cryptoOpsClient,
        certificateFactory
    )

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
                    "one" to GatewayTlsCertificates("id", certificates.keys.toList())
                )
            )

            assertThat(dynamicKeyStore.aliasToCertificates["one"]).containsAll(certificates.values)
        }

        @Test
        fun `onNext remove data with null value`() {
            processorForKeystore.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", certificates.keys.toList())
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
                    GatewayTlsCertificates("id", certificates.keys.toList()),
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

            processorForKeystore.firstValue.onSnapshot(
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
            mockConstruction(KeyStoreFactory::class.java).use {
                dynamicKeyStore.keyStore

                verify(it.constructed().first()).createDelegatedKeyStore()
            }
        }
    }
}
