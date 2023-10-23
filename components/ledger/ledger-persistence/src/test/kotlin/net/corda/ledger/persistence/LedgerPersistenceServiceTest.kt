package net.corda.ledger.persistence

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestSubscriptionFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream

class LedgerPersistenceServiceTest {
    companion object {
        @JvmStatic
        fun dependants(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
            )
        }
    }

    private val sandboxGroupContextComponent = mock<SandboxGroupContextComponent>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()

    private val kafkaSubscription1 = mock<Subscription<String, LedgerPersistenceRequest>>()
    private val kafkaSubscription2 = mock<Subscription<String, LedgerPersistenceRequest>>()
    private val rpcSubscription = mock<RPCSubscription<LedgerPersistenceRequest, FlowEvent>>()

    private val ledgerPersistenceRequestSubscriptionFactory =
        mock<LedgerPersistenceRequestSubscriptionFactory>().apply {
            whenever(this.create(MINIMUM_SMART_CONFIG)).thenReturn(kafkaSubscription1, kafkaSubscription2)
            whenever(this.createRpcSubscription()).thenReturn(rpcSubscription)
        }

    private val exampleConfig = mapOf(
        ConfigKeys.MESSAGING_CONFIG to MINIMUM_SMART_CONFIG,
        ConfigKeys.BOOT_CONFIG to MINIMUM_SMART_CONFIG
    )

    @Test
    fun `on configuration event creates and starts subscription`() {
        getTokenCacheComponentTestContext().run {
            val subscription = mock<Subscription<String, LedgerPersistenceRequest>>()
            whenever(ledgerPersistenceRequestSubscriptionFactory.create(MINIMUM_SMART_CONFIG)).thenReturn(subscription)

            val rpcSubscription = mock<RPCSubscription<LedgerPersistenceRequest, FlowEvent>>()
            whenever(ledgerPersistenceRequestSubscriptionFactory.createRpcSubscription()).thenReturn(rpcSubscription)

            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)

            verify(ledgerPersistenceRequestSubscriptionFactory).create(MINIMUM_SMART_CONFIG)
            verify(ledgerPersistenceRequestSubscriptionFactory).createRpcSubscription()

            verify(subscription).start()
            verify(rpcSubscription).start()
        }
    }

    @Test
    fun `on configuration event closes existing subscription`() {
        getTokenCacheComponentTestContext().run {
            testClass.start()
            bringDependenciesUp()
            verify(ledgerPersistenceRequestSubscriptionFactory).createRpcSubscription()
            verify(rpcSubscription).start()

            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)
            verify(ledgerPersistenceRequestSubscriptionFactory).create(MINIMUM_SMART_CONFIG)
            verify(kafkaSubscription1).start()

            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)
            verify(kafkaSubscription1).close()
            verify(ledgerPersistenceRequestSubscriptionFactory, times(2)).create(MINIMUM_SMART_CONFIG)
            verify(kafkaSubscription2).start()
        }
    }

    @Test
    fun `on all dependents up persistence service should not be up`() {
        getTokenCacheComponentTestContext().run {
            testClass.start()
            bringDependenciesUp()

            verifyIsDown<LedgerPersistenceService>()
        }
    }

    @ParameterizedTest(name = "on component {0} going down the persistence service should go down")
    @MethodSource("dependants")
    fun `on any dependent going down the persistence service should go down`(name: LifecycleCoordinatorName) {
        getTokenCacheComponentTestContext().run {
            testClass.start()

            bringDependenciesUp()
            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)
            verifyIsUp<LedgerPersistenceService>()

            bringDependencyDown(name)

            verifyIsDown<LedgerPersistenceService>()
        }
    }

    @ParameterizedTest(name = "on component {0} going to error the persistence service component should go to down")
    @MethodSource("dependants")
    fun `on any dependent going to error the token persistence service should go to down`(
        name: LifecycleCoordinatorName
    ) {
        getTokenCacheComponentTestContext().run {
            testClass.start()

            bringDependenciesUp()
            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)
            verifyIsUp<LedgerPersistenceService>()

            setDependencyToError(name)

            verifyIsDown<LedgerPersistenceService>()
        }
    }

    private fun getTokenCacheComponentTestContext(): LifecycleTest<LedgerPersistenceService> {
        return LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<SandboxGroupContextComponent>()
            addDependency<VirtualNodeInfoReadService>()
            addDependency<CpiInfoReadService>()

            LedgerPersistenceService(
                coordinatorFactory,
                configReadService,
                sandboxGroupContextComponent,
                virtualNodeInfoReadService,
                cpiInfoReadService,
                ledgerPersistenceRequestSubscriptionFactory
            )
        }
    }
}
