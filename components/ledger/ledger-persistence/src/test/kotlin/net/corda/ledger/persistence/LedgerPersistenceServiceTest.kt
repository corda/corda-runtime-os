package net.corda.ledger.persistence

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.processor.LedgerPersistenceRequestSubscriptionFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.stream.Stream

class LedgerPersistenceServiceTest {
    companion object {
        @JvmStatic
        fun dependants(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
            )
        }
    }

    private val sandboxGroupContextComponent = mock<SandboxGroupContextComponent>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService>()
    private val cpiInfoReadService = mock<CpiInfoReadService>()
    private val rpcSubscription = mock<RPCSubscription<LedgerPersistenceRequest, FlowEvent>>()

    private val ledgerPersistenceRequestSubscriptionFactory =
        mock<LedgerPersistenceRequestSubscriptionFactory>().apply {
            whenever(this.createRpcSubscription()).thenReturn(rpcSubscription)
        }

    @ParameterizedTest(name = "on component {0} going down the persistence service should go down")
    @MethodSource("dependants")
    fun `on any dependent going down the persistence service should go down`(name: LifecycleCoordinatorName) {
        getTokenCacheComponentTestContext().run {
            testClass.start()
            bringDependenciesUp()
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
            verifyIsUp<LedgerPersistenceService>()

            setDependencyToError(name)

            verifyIsDown<LedgerPersistenceService>()
        }
    }

    private fun getTokenCacheComponentTestContext(): LifecycleTest<LedgerPersistenceService> {
        return LifecycleTest {
            addDependency<SandboxGroupContextComponent>()
            addDependency<VirtualNodeInfoReadService>()
            addDependency<CpiInfoReadService>()

            LedgerPersistenceService(
                coordinatorFactory,
                sandboxGroupContextComponent,
                virtualNodeInfoReadService,
                cpiInfoReadService,
                ledgerPersistenceRequestSubscriptionFactory
            )
        }
    }
}
