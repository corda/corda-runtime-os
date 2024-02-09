package net.corda.processors.crypto.internal

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.config.impl.ALIAS
import net.corda.crypto.config.impl.CACHING
import net.corda.crypto.config.impl.DEFAULT
import net.corda.crypto.config.impl.DEFAULT_WRAPPING_KEY
import net.corda.crypto.config.impl.HSM
import net.corda.crypto.config.impl.PASSPHRASE
import net.corda.crypto.config.impl.RETRYING
import net.corda.crypto.config.impl.SALT
import net.corda.crypto.config.impl.WRAPPING_KEYS
import net.corda.crypto.core.aes.WrappingKeyImpl
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.service.impl.bus.CryptoRekeyBusProcessor
import net.corda.crypto.service.impl.bus.CryptoRewrapBusProcessor
import net.corda.crypto.service.impl.rpc.CryptoFlowOpsProcessor
import net.corda.crypto.service.impl.rpc.SessionDecryptionProcessor
import net.corda.crypto.service.impl.rpc.SessionEncryptionProcessor
import net.corda.crypto.softhsm.impl.HSMRepositoryImpl
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.ops.encryption.request.DecryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.request.EncryptRpcCommand
import net.corda.data.crypto.wire.ops.encryption.response.DecryptionOpsResponse
import net.corda.data.crypto.wire.ops.encryption.response.EncryptionOpsResponse
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.key.rotation.IndividualKeyRotationRequest
import net.corda.data.crypto.wire.ops.key.rotation.KeyRotationRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.data.flow.event.FlowEvent
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.SubscriptionBase
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.StateManagerConfig
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.TypedQuery

class CryptoProcessorImplTest {

    companion object {
        private const val ALPHA_KEY_NAME = "alpha"
        private const val ALPHA_KEY_PASSPHRASE = "friend"
        private const val ALPHA_KEY_SALT = "speak"

        // Tempting to use SmartConfig here even from unit tests, but probably better to mock it out
        // since SmartConfig is Corda corda. Or could we use TypeSafeConfig directly heere?
        // TODO remove hard coded strings e.g. "expireAfterAccessMins" and instead use string constants
        //   from CryptoConfigUtils exclusively
        private val cryptoConfig = mock<SmartConfig>().also {
            val cachingConfig = mock<SmartConfig>().also {
                val expireAfterAccessMinsConfig = mock<SmartConfig>().also {
                    whenever(it.getLong(DEFAULT)).thenReturn(1)
                }
                val maximumSizeConfig = mock<SmartConfig>().also {
                    whenever(it.getLong(DEFAULT)).thenReturn(1)
                }
                whenever(it.getConfig("expireAfterAccessMins")).thenReturn(expireAfterAccessMinsConfig)
                whenever(it.getConfig("maximumSize")).thenReturn(maximumSizeConfig)
            }

            val retryingConfig = mock<SmartConfig>().also {
                val maxAttemptsConfig = mock<SmartConfig>().also {
                    whenever(it.getLong(DEFAULT)).thenReturn(1)
                }
                val waitBetweenMillsConfig = mock<SmartConfig>().also {
                    whenever(it.getLong(DEFAULT)).thenReturn(1)
                }
                whenever(it.getConfig("maxAttempts")).thenReturn(maxAttemptsConfig)
                whenever(it.getConfig("waitBetweenMills")).thenReturn(waitBetweenMillsConfig)
            }
            val wrappingKeysList = listOf(mock<SmartConfig>().also {
                whenever(it.getString(ALIAS)).thenReturn(ALPHA_KEY_NAME)
                whenever(it.getString(SALT)).thenReturn(ALPHA_KEY_SALT)
                whenever(it.getString(PASSPHRASE)).thenReturn(ALPHA_KEY_PASSPHRASE)
            })
            val hsmConfig = mock<SmartConfig>().also {
                whenever(it.getConfigList(eq(WRAPPING_KEYS))).thenReturn(wrappingKeysList)
                // this must match the ALIAS of the one wrapping key we set up in wrappingKeysList
                whenever(it.getString(DEFAULT_WRAPPING_KEY)).thenReturn(ALPHA_KEY_NAME)
            }
            whenever(it.getConfig(eq(CACHING))).thenReturn(cachingConfig)
            whenever(it.getConfig(eq(RETRYING))).thenReturn(retryingConfig)
            whenever(it.getConfig(eq(HSM))).thenReturn(hsmConfig)
        }
    }

    /**
     * We have a lot of mock subscriptions to test and in the main we perform the same validation on each of them.
     * This class helps that without having to write lots of boiler plate code.
     */
    class MockSubscriptions {
        private val subscriptionList = mutableListOf<SubscriptionBase>()

        val keyRotationSubscription =
            mock<Subscription<String, KeyRotationRequest>>().also { subscriptionList.add(it) }
        val keyRewrapSubscription =
            mock<Subscription<String, IndividualKeyRotationRequest>>().also { subscriptionList.add(it) }
        val flowOpsRpcSubscription =
            mock<RPCSubscription<FlowOpsRequest, FlowEvent>>().also { subscriptionList.add(it) }
        val rpcOpsSubscription =
            mock<RPCSubscription<RpcOpsRequest, RpcOpsResponse>>().also { subscriptionList.add(it) }
        val encryptionSubscription =
            mock<RPCSubscription<EncryptRpcCommand, EncryptionOpsResponse>>().also { subscriptionList.add(it) }
        val decryptionSubscription =
            mock<RPCSubscription<DecryptRpcCommand, DecryptionOpsResponse>>().also { subscriptionList.add(it) }
        val hsmRegSubscription =
            mock<RPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>>().also { subscriptionList.add(it) }

        init {
            // Sanity check all subscriptions are in list
            assertThat(subscriptionList).hasSize(7)
        }

        /**
         * Clears invocations, so do any specific subscription verification before calling
         */
        fun verifyAllSubscriptionsStarted() {
            subscriptionList.forEach {
                verify(it).start()
                clearInvocations(it)
            }
        }

        /**
         * Clears invocations, so do any specific subscription verification before calling
         */
        @SuppressWarnings("SpreadOperator")
        fun verifyAllSubscriptionsClosedAndRestarted() {
            inOrder(*subscriptionList.toTypedArray()).also { inOrder ->
                subscriptionList.forEach {
                    inOrder.verify(it).close()
                    inOrder.verify(it).start()
                    clearInvocations(it)
                }
            }
        }
    }

    @Test
    fun `bus subscriptions created on ConfigChangedEvent, closed and re-created on new ConfigChangedEvent`() {
        val mockSubscriptions = MockSubscriptions()
        val subscriptionFactory = mockSubscriptionFactory(mockSubscriptions)
        val mockHsmAssociation = mock<HSMAssociationInfo>()
        val mockHsmAssociationInfo = mock<HSMAssociationInfo>() {
            on { masterKeyAlias }.doReturn("masterKeyAlias")
        }
        mockConstruction(HSMRepositoryImpl::class.java) { mock, _ ->
            whenever(mock.findTenantAssociation(any(), any())).doReturn(mockHsmAssociation)
            whenever(mock.createOrLookupCategoryAssociation(any(), any(), any())).doReturn(mockHsmAssociationInfo)
        }

        val virtualNodeInfo = mock<VirtualNodeInfo> {
            on { cryptoDmlConnectionId } doReturn UUID.randomUUID()
        }

        val mockDbConnectionManager = mockDbConnectionManager()
        val stateManager: StateManager = mock()
        val stateManagerFactory: StateManagerFactory = mock {
            on { create(any(), any()) } doReturn stateManager
        }

        // Keep track of any publishers created
        val publisherList = mutableListOf<Publisher>()
        val publisherFactory = mock<PublisherFactory>() {
            on { createPublisher(any(), any()) }.doAnswer {
                mock<Publisher>().also {
                    publisherList.add(it)
                }
            }
        }

        LifecycleTest {
            addDependency<LifecycleCoordinatorFactory>()
            addDependency<ConfigurationReadService>()

            addDependency<JpaEntitiesRegistry>()
            addDependency<DbConnectionManager>()
            addDependency<VirtualNodeInfoReadService>()
            addDependency<SubscriptionFactory>()


            val cryptoProcessor = CryptoProcessorImpl(
                coordinatorFactory = coordinatorFactory,
                configurationReadService = configReadService,
                jpaEntitiesRegistry = mock(),
                dbConnectionManager = mockDbConnectionManager,
                virtualNodeInfoReadService = mock {
                    on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
                },
                subscriptionFactory = subscriptionFactory,
                externalEventResponseFactory = mock(),
                keyEncodingService = mock(),
                layeredPropertyMapFactory = mock(),
                digestService = mock(),
                schemeMetadata = mock(),
                publisherFactory = publisherFactory,
                stateManagerFactory = stateManagerFactory,
                cordaAvroSerializationFactory = mock()
            ).apply {
                // Must start and pass some sort of boot config
                start(mock {
                    on { hasPath(StateManagerConfig.STATE_MANAGER) } doReturn true
                    on { getConfig(StateManagerConfig.STATE_MANAGER) } doReturn mock()
                })
            }
            cryptoProcessor.lifecycleCoordinator
        }.run {
            testClass.start()
            bringDependenciesUp()
            verify(configReadService).registerComponentForUpdates(any(), any())
            verifyIsDown<CryptoProcessor>()

            sendConfigUpdate<CryptoProcessor>(
                mapOf(
                    CRYPTO_CONFIG to cryptoConfig, MESSAGING_CONFIG to mock(),
                    ConfigKeys.STATE_MANAGER_CONFIG to mock()
                )
            )

            verifyIsUp<CryptoProcessor>()
            mockSubscriptions.verifyAllSubscriptionsStarted()

            // We only publish out of the rekey processor, so we should have 1 publisher
            val originalPublisher = publisherList.single()

            sendConfigUpdate<CryptoProcessor>(
                mapOf(
                    CRYPTO_CONFIG to cryptoConfig, MESSAGING_CONFIG to mock(),
                    ConfigKeys.STATE_MANAGER_CONFIG to mock()
                )
            )

            verifyIsUp<CryptoProcessor>()

            // Expect a new publisher per config change
            assertThat(publisherList).hasSize(2)

            // Check original publisher created was closed on the config change, after the subscription was closed
            inOrder(originalPublisher, mockSubscriptions.keyRotationSubscription).apply {
                verify(mockSubscriptions.keyRotationSubscription).close()
                verify(originalPublisher).close()
            }

            mockSubscriptions.verifyAllSubscriptionsClosedAndRestarted()
        }
    }

    private fun mockDbConnectionManager(): DbConnectionManager {
        // This is very susceptible to changes in db access, at the moment it is based around the TenantInfoService
        // ensuring a wrapping key exists when the first call to populate is made, which is done on the first CryptoProcessor
        // config changes. Our mock is set up to return an existing WrappingKeyEntity as if it exists in the database,
        // but from then on subsequent attempts to fetch it will come from the crypto service's internal cache.
        val mockTypedQuery = mock<TypedQuery<*>>()
        whenever(mockTypedQuery.setParameter(any<String>(), any<Any>())).thenReturn(mockTypedQuery)
        whenever(mockTypedQuery.setMaxResults(any())).thenReturn(mockTypedQuery)
        val alphaWrappingKey = WrappingKeyImpl.derive(CipherSchemeMetadataImpl(), ALPHA_KEY_PASSPHRASE, ALPHA_KEY_SALT)
        // Crypto service checks for valid decryptable key material before adding to the cache, because the cache holds already
        // decrypted wrapping keys. So here we wrap the alphaWrappingKey with itself to achieve this, even though it's not a
        // sensible real world scenario. It makes the dummyKeyMaterial decryptable with the alphaWrappingKey key which is all
        // that matters.
        val dummyKeyMaterial = alphaWrappingKey.wrap(alphaWrappingKey)
        whenever(mockTypedQuery.resultList).thenReturn(
            listOf(
                WrappingKeyEntity(
                    id = UUID.randomUUID(),
                    generation = 1,
                    alias = "alias",
                    created = Instant.now(),
                    rotationDate = LocalDate.parse("9999-12-31").atStartOfDay().toInstant(ZoneOffset.UTC),
                    encodingVersion = 1,
                    algorithmName = "AES",
                    keyMaterial = dummyKeyMaterial,
                    isParentKeyManaged = false,
                    parentKeyReference = ALPHA_KEY_NAME
                )
            )
        )

        val mockEntityManager = mock<EntityManager>() {
            on { createQuery(any<String>(), any<Class<*>>()) }.doReturn(mockTypedQuery)
        }
        val mockEntityManagerFactory = mock<EntityManagerFactory>() {
            on { createEntityManager() }.doReturn(mockEntityManager)
        }
        // We are only mocking DbConnectionManager for the initial check that a wrapping key exists, fail the test if
        // we get multiple calls to create an EMF
        val mockDbConnectionManager = mock<DbConnectionManager>() {
            on { getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML) }.doReturn(
                mockEntityManagerFactory
            )
        }
        return mockDbConnectionManager
    }

    private fun mockSubscriptionFactory(mockSubscriptions: MockSubscriptions): SubscriptionFactory {
        val subscriptionFactory = mock<SubscriptionFactory>().also {

            whenever(
                it.createDurableSubscription<String, KeyRotationRequest>(
                    any(),
                    any<CryptoRekeyBusProcessor>(),
                    any(),
                    anyOrNull()
                )
            ).thenReturn(mockSubscriptions.keyRotationSubscription)

            whenever(
                it.createDurableSubscription<String, IndividualKeyRotationRequest>(
                    any(),
                    any<CryptoRewrapBusProcessor>(),
                    any(),
                    anyOrNull()
                )
            ).thenReturn(mockSubscriptions.keyRewrapSubscription)

            whenever(
                it.createRPCSubscription(
                    eq(
                        RPCConfig(
                            "crypto.ops.rpc",
                            "crypto.ops.rpc",
                            Schemas.Crypto.RPC_OPS_MESSAGE_TOPIC,
                            RpcOpsRequest::class.java,
                            RpcOpsResponse::class.java
                        )
                    ),
                    any(),
                    any()
                )
            ).thenReturn(mockSubscriptions.rpcOpsSubscription)

            whenever(
                it.createHttpRPCSubscription(
                    any(),
                    any<SessionDecryptionProcessor>()
                )
            ).thenReturn(mockSubscriptions.decryptionSubscription)

            whenever(
                it.createHttpRPCSubscription(
                    any(),
                    any<SessionEncryptionProcessor>()
                )
            ).thenReturn(mockSubscriptions.encryptionSubscription)

            whenever(
                it.createRPCSubscription(
                    eq(
                        RPCConfig(
                            "crypto.hsm.rpc.registration",
                            "crypto.hsm.rpc.registration",
                            Schemas.Crypto.RPC_HSM_REGISTRATION_MESSAGE_TOPIC,
                            HSMRegistrationRequest::class.java,
                            HSMRegistrationResponse::class.java
                        )
                    ),
                    any(),
                    any()
                )
            ).thenReturn(mockSubscriptions.hsmRegSubscription)

            whenever(
                it.createHttpRPCSubscription(
                    any(),
                    any<CryptoFlowOpsProcessor>()
                )
            ).thenReturn(mockSubscriptions.flowOpsRpcSubscription)
        }
        return subscriptionFactory
    }
}