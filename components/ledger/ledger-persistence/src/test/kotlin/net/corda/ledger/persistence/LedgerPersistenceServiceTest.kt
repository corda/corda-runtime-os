package net.corda.ledger.persistence

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.processor.PersistenceRequestSubscriptionFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.Subscription
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
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

    private val subscription1 = mock<Subscription<String, LedgerPersistenceRequest>>()
    private val subscription2 = mock<Subscription<String, LedgerPersistenceRequest>>()
    private val persistenceRequestSubscriptionFactory = mock<PersistenceRequestSubscriptionFactory>().apply {
        whenever(this.create(MINIMUM_SMART_CONFIG)).thenReturn(subscription1, subscription2)
    }

    private val exampleConfig = mapOf(
        ConfigKeys.MESSAGING_CONFIG to MINIMUM_SMART_CONFIG,
        ConfigKeys.BOOT_CONFIG to MINIMUM_SMART_CONFIG
    )

    @Test
    fun `on configuration event creates and starts subscription`() {
        val subscription = mock<Subscription<String, LedgerPersistenceRequest>>()
        whenever(persistenceRequestSubscriptionFactory.create(MINIMUM_SMART_CONFIG)).thenReturn(subscription)

        getTokenCacheComponentTestContext().run {
            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)

            verify(persistenceRequestSubscriptionFactory).create(MINIMUM_SMART_CONFIG)
            verify(subscription).start()
        }
    }

    @Test
    fun `on configuration event closes existing subscription`() {
        getTokenCacheComponentTestContext().run {
            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)

            verify(persistenceRequestSubscriptionFactory).create(MINIMUM_SMART_CONFIG)
            verify(subscription1).start()

            sendConfigUpdate<LedgerPersistenceService>(exampleConfig)
            verify(subscription1).close()
            verify(subscription2).start()
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
                persistenceRequestSubscriptionFactory
            )
        }
    }
}