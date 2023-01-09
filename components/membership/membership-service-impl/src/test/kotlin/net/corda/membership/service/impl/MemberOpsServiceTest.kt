package net.corda.membership.service.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.RegistrationProxy
import net.corda.membership.service.MemberOpsService
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class MemberOpsServiceTest {
    private val subName = LifecycleCoordinatorName("RPC_SUBSCRIPTION")
    private val subscription: RPCSubscription<MembershipRpcRequest, MembershipRpcResponse> = mock {
        on { subscriptionName } doReturn subName
    }
    private val subscriptionFactory: SubscriptionFactory = mock {
        on { createRPCSubscription(
                any(), any(), any<RPCResponderProcessor<MembershipRpcRequest, MembershipRpcResponse>>()
            )
        } doReturn subscription
    }

    private val registrationProxy: RegistrationProxy = mock()
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock()
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider = mock()
    private val membershipQueryClient: MembershipQueryClient = mock()

    private val messagingConfig: SmartConfig = mock()
    private val bootConfig: SmartConfig = mock {
        on { withFallback(messagingConfig) } doReturn messagingConfig
    }

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig
    )


    @Test
    fun `start event does not change status to UP`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            verifyIsDown<MemberOpsService>()
        }
    }

    @Test
    fun `status changes to UP and subscription gets created, once all dependencies are UP and config has been received`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<MemberOpsService>(configs)
            verify(subscription).start()
            verifyIsUp<MemberOpsService>()
        }
    }

    @Test
    fun `component remains UP when new config change is received`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<MemberOpsService>(configs)
            verifyIsUp<MemberOpsService>()

            sendConfigUpdate<MemberOpsService>(configs)
            verifyIsUp<MemberOpsService>()
        }
    }

    @Test
    fun `stop event takes the component DOWN`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<MemberOpsService>(configs)

            testClass.stop()
            verifyIsDown<MemberOpsService>()
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if subscription goes DOWN then UP`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<MemberOpsService>(configs)

            verifyIsUp<MemberOpsService>()

            toggleDependency(subName, {
                verifyIsDown<MemberOpsService>()
            }, {
                verifyIsUp<MemberOpsService>()
            })
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if a dependent component has error state and comes back`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<MemberOpsService>(configs)

            setDependencyToError<VirtualNodeInfoReadService>()
            verifyIsDown<MemberOpsService>()
            bringDependencyUp<VirtualNodeInfoReadService>()

            sendConfigUpdate<MemberOpsService>(configs)
            verifyIsUp<MemberOpsService>()
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if subscription has error state and comes back`() {
        getMemberOpsServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<MemberOpsService>(configs)

            setDependencyToError(subName)
            verifyIsDown<MemberOpsService>()
            bringDependencyUp(subName)
            verifyIsUp<MemberOpsService>()
        }
    }

    private fun getMemberOpsServiceTestContext(): LifecycleTest<MemberOpsService> {
        return LifecycleTest {
            addDependency(subName)
            addDependency<ConfigurationReadService>()
            addDependency<RegistrationProxy>()
            addDependency<VirtualNodeInfoReadService>()
            addDependency<MembershipGroupReaderProvider>()
            addDependency<MembershipQueryClient>()

            MemberOpsServiceImpl(
                coordinatorFactory,
                subscriptionFactory,
                configReadService,
                registrationProxy,
                virtualNodeInfoReadService,
                membershipGroupReaderProvider,
                membershipQueryClient,
                mock(),
                mock(),
            )
        }
    }
}