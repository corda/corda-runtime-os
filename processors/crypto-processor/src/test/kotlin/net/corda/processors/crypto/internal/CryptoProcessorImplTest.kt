package net.corda.processors.crypto.internal

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
import net.corda.crypto.softhsm.impl.HSMRepositoryImpl
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.processors.crypto.CryptoProcessor
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class CryptoProcessorImplTest {

    companion object {
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
                whenever(it.getString(ALIAS)).thenReturn("alpha")
                whenever(it.getString(SALT)).thenReturn("speak")
                whenever(it.getString(PASSPHRASE)).thenReturn("friend")
            })
            val hsmConfig = mock<SmartConfig>().also {
                whenever(it.getConfigList(eq(WRAPPING_KEYS))).thenReturn(wrappingKeysList)
                // this must match the ALIAS of the one wrapping key we set up in wrappingKeysList
                whenever(it.getString(DEFAULT_WRAPPING_KEY)).thenReturn("alpha")
            }
            whenever(it.getConfig(eq(CACHING))).thenReturn(cachingConfig)
            whenever(it.getConfig(eq(RETRYING))).thenReturn(retryingConfig)
            whenever(it.getConfig(eq(HSM))).thenReturn(hsmConfig)
        }
    }

    @Test
    fun `bus subscriptions created on ConfigChangedEvent, closed and re-created on new ConfigChangedEvent`() {
        val flowOpsSubscription = mock<Subscription<String, FlowOpsRequest>>()
        val rpcOpsSubscription = mock<RPCSubscription<RpcOpsRequest, RpcOpsResponse>>()
        val hsmRegSubscription = mock<RPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>>()
        val subscriptionFactory = mock<SubscriptionFactory>().also {
            whenever(it.createDurableSubscription<String, FlowOpsRequest>(any(), any(), any(), anyOrNull()))
                .thenReturn(flowOpsSubscription)
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
            )
                .thenReturn(rpcOpsSubscription)
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
            )
                .thenReturn(hsmRegSubscription)
        }
        val mockHsmAssociation = mock<HSMAssociationInfo>()
        mockConstruction(HSMRepositoryImpl::class.java) { mock, _ ->
            whenever(mock.findTenantAssociation(any(), any())).doReturn(mockHsmAssociation)

            LifecycleTest {
                addDependency<LifecycleCoordinatorFactory>()
                addDependency<ConfigurationReadService>()

                addDependency<JpaEntitiesRegistry>()
                addDependency<DbConnectionManager>()
                addDependency<VirtualNodeInfoReadService>()
                addDependency<SubscriptionFactory>()
                val virtualNodeInfo = mock<VirtualNodeInfo> {
                    on { cryptoDmlConnectionId } doReturn UUID.randomUUID()
                }

                val cryptoProcessor = CryptoProcessorImpl(
                    coordinatorFactory = coordinatorFactory,
                    configurationReadService = configReadService,
                    jpaEntitiesRegistry = mock(),
                    dbConnectionManager = mock(),
                    virtualNodeInfoReadService = mock {
                        on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
                    },
                    subscriptionFactory = subscriptionFactory,
                    externalEventResponseFactory = mock(),
                    keyEncodingService = mock(),
                    layeredPropertyMapFactory = mock(),
                    digestService = mock(),
                    schemeMetadata = mock(),
                    publisherFactory = mock(),)
                cryptoProcessor.lifecycleCoordinator
            }.run {
                testClass.start()
                bringDependenciesUp()
                verify(configReadService, times(1)).registerComponentForUpdates(any(), any())
                verifyIsDown<CryptoProcessor>()

                sendConfigUpdate<CryptoProcessor>(mapOf(CRYPTO_CONFIG to cryptoConfig, MESSAGING_CONFIG to mock()))

                verifyIsUp<CryptoProcessor>()
                verify(flowOpsSubscription, times(1)).start()
                verify(rpcOpsSubscription, times(1)).start()
                verify(hsmRegSubscription, times(1)).start()

                sendConfigUpdate<CryptoProcessor>(mapOf(CRYPTO_CONFIG to cryptoConfig, MESSAGING_CONFIG to mock()))

                verify(flowOpsSubscription, times(1)).close()
                verify(rpcOpsSubscription, times(1)).close()
                verify(hsmRegSubscription, times(1)).close()

                verify(flowOpsSubscription, times(2)).start()
                verify(rpcOpsSubscription, times(2)).start()
                verify(hsmRegSubscription, times(2)).start()
            }
        }
    }
}