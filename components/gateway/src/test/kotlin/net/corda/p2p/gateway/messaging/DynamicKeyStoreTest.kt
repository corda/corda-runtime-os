package net.corda.p2p.gateway.messaging

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.GatewayTlsCertificates
import net.corda.p2p.test.stub.crypto.processor.StubCryptoProcessor
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
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
    private val processor = argumentCaptor<CompactedProcessor<String, GatewayTlsCertificates>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), eq(nodeConfiguration)) } doReturn subscription
    }
    private val certificateFactory = mock<CertificateFactory>()
    private var createResources: ((ResourcesHolder) -> CompletableFuture<Unit>)? = null
    private val dominoTile = mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as? ((ResourcesHolder) -> CompletableFuture<Unit>)
    }
    private val subscriptionDominoTile = mockConstruction(SubscriptionDominoTile::class.java)
    private val signer = mockConstruction(StubCryptoProcessor::class.java)

    private val dynamicKeyStore = DynamicKeyStore(
        lifecycleCoordinatorFactory,
        subscriptionFactory,
        nodeConfiguration,
        certificateFactory,
    )

    @AfterEach
    fun cleanUp() {
        subscriptionDominoTile.close()
        dominoTile.close()
        signer.close()
    }

    @Nested
    inner class CreateResourcesTest {
        private val resourcesHolder = ResourcesHolder()

        @Test
        fun `create resources will not complete without snapshots`() {
            val future = createResources?.invoke(resourcesHolder)

            assertThat(future).isNotCompleted
        }

        @Test
        fun `create resources will complete with snapshots`() {
            val future = createResources?.invoke(resourcesHolder)

            processor.firstValue.onSnapshot(emptyMap())

            assertThat(future).isCompleted
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
            processor.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", certificates.keys.toList())
                )
            )

            assertThat(dynamicKeyStore.aliasToCertificates["one"]).containsAll(certificates.values)
        }

        @Test
        fun `onNext remove data with null value`() {
            processor.firstValue.onSnapshot(
                mapOf(
                    "one" to GatewayTlsCertificates("id", certificates.keys.toList())
                )
            )

            processor.firstValue.onNext(
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
            processor.firstValue.onNext(
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

            processor.firstValue.onSnapshot(
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
        fun `sign with known publicKey will send the correct data`() {
            dynamicKeyStore.sign(publicKeyOne, spec, data)

            verify(signer.constructed().first()).sign(tenantIdOne, publicKeyOne, spec, data)
        }

        @Test
        fun `sign with known publicKey will return the correct data`() {
            val returnedData = "ok".toByteArray()
            whenever(signer.constructed().first().sign(any(), any(), any(), any())).doReturn(returnedData)

            assertThat(dynamicKeyStore.sign(publicKeyOne, spec, data)).isEqualTo(returnedData)
        }

        @Test
        fun `onNext will remove the public key`() {
            processor.firstValue.onNext(
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
            processor.firstValue.onNext(
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
