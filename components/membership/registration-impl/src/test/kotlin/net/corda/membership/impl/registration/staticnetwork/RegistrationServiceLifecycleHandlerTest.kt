package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.data.KeyValuePairList
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.Resource
import net.corda.lifecycle.createCoordinator
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.registration.staticnetwork.TestUtils.Companion.configs
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.schema.validation.MembershipSchemaValidatorFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RegistrationServiceLifecycleHandlerTest {
    private val componentHandle: RegistrationHandle = mock()
    private val subRegistrationHandle: RegistrationHandle = mock()
    private val configHandle: Resource = mock()

    private val groupPolicyProvider: GroupPolicyProvider = mock()

    private val configurationReadService: ConfigurationReadService = mock {
        on { registerComponentForUpdates(any(), any()) } doReturn configHandle
    }

    private val publisher: Publisher = mock()

    private val publisherFactory: PublisherFactory = mock {
        on { createPublisher(any(), any()) } doReturn publisher
    }

    private val subName = LifecycleCoordinatorName("COMPACTED_SUBSCRIPTION")

    private val mockSubscription: CompactedSubscription<String, KeyValuePairList> = mock {
        on { subscriptionName } doReturn subName
    }

    private val subscriptionFactory: SubscriptionFactory = mock {
        on {
            createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, KeyValuePairList>>(),
                any()
            )
        } doReturn mockSubscription
    }

    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(any()) } doReturn componentHandle
        on { followStatusChangesByName(setOf(mockSubscription.subscriptionName)) } doReturn subRegistrationHandle
    }

    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }

    private val memberInfoFactory: MemberInfoFactory = mock()

    private val hsmRegistrationClient: HSMRegistrationClient = mock()
    private val membershipSchemaValidatorFactory: MembershipSchemaValidatorFactory = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock()

    private val staticMemberRegistrationService = StaticMemberRegistrationService(
        groupPolicyProvider,
        publisherFactory,
        subscriptionFactory,
        mock(),
        mock(),
        configurationReadService,
        coordinatorFactory,
        hsmRegistrationClient,
        memberInfoFactory,
        mock(),
        mock(),
        membershipSchemaValidatorFactory,
        mock(),
        platformInfoProvider,
        mock(),
        virtualNodeInfoReadService,
        mock(),
    )

    private val registrationServiceLifecycleHandler = RegistrationServiceLifecycleHandler(
        staticMemberRegistrationService
    )

    @Test
    fun `Start event does not immediately move to UP status`() {
        val context = getTestContext()
        context.run {
            testClass.start()

            context.verifyIsDown<TestRegistrationComponent>()
        }
    }

    @Test
    fun `UP is posted once all dependencies are UP and configuration has been provided`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            context.verifyIsUp<TestRegistrationComponent>()
            assertNotNull(context.testClass.registrationServiceLifecycleHandler.publisher)
            assertNotNull(context.testClass.registrationServiceLifecycleHandler.groupParametersCache)
        }
    }

    @Test
    fun `component remains UP if the config is changed`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            context.verifyIsUp<TestRegistrationComponent>()

            // A config update of any variety should not trigger the test component to go down. This can be verified by
            // sending the same thing again, as the code will still respond to this event as if it were a change.
            sendConfigUpdate<TestRegistrationComponent>(configs)
            context.verifyIsUp<TestRegistrationComponent>()
        }
    }

    @Test
    fun `component is DOWN after a stop event and the publisher is closed`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            context.verifyIsUp<TestRegistrationComponent>()
            testClass.stop()
            context.verifyIsDown<TestRegistrationComponent>()
        }

        assertThrows<IllegalArgumentException> { registrationServiceLifecycleHandler.publisher }
    }

    @Test
    fun `component goes DOWN if one of its dependencies goes DOWN`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            toggleDependency<GroupPolicyProvider>({
                context.verifyIsDown<TestRegistrationComponent>()
            }, {
                context.verifyIsDown<TestRegistrationComponent>()
            })
            sendConfigUpdate<TestRegistrationComponent>(configs)
            context.verifyIsUp<TestRegistrationComponent>()

            toggleDependency<ConfigurationReadService>({
                context.verifyIsDown<TestRegistrationComponent>()
            }, {
                context.verifyIsDown<TestRegistrationComponent>()
            })
            sendConfigUpdate<TestRegistrationComponent>(configs)
            context.verifyIsUp<TestRegistrationComponent>()

            toggleDependency<HSMRegistrationClient>({
                context.verifyIsDown<TestRegistrationComponent>()
            }, {
                context.verifyIsDown<TestRegistrationComponent>()
            })
            sendConfigUpdate<TestRegistrationComponent>(configs)
            context.verifyIsUp<TestRegistrationComponent>()
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if subscription goes DOWN then UP`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            context.verifyIsUp<TestRegistrationComponent>()

            toggleDependency(subName, {
                context.verifyIsDown<TestRegistrationComponent>()
            }, {
                context.verifyIsUp<TestRegistrationComponent>()
            })
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if a dependent component errors and comes back`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)
            context.verifyIsUp<TestRegistrationComponent>()

            setDependencyToError<HSMRegistrationClient>()
            context.verifyIsDown<TestRegistrationComponent>()
            bringDependencyUp<HSMRegistrationClient>()

            // Model a config update coming back due to us re-registering with the config read service.
            sendConfigUpdate<TestRegistrationComponent>(configs)
            context.verifyIsUp<TestRegistrationComponent>()
        }
    }

    @Test
    fun `component handles a subscription error and restart`() {
        val context = getTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            context.verifyIsUp<TestRegistrationComponent>()

            setDependencyToError(subName)
            context.verifyIsDown<TestRegistrationComponent>()
            bringDependencyUp(subName)
            context.verifyIsUp<TestRegistrationComponent>()
        }
    }

    private class TestRegistrationComponent(
        coordinatorFactory: LifecycleCoordinatorFactory,
        val registrationServiceLifecycleHandler: RegistrationServiceLifecycleHandler
    ) : Lifecycle {

        private val coordinator =
            coordinatorFactory.createCoordinator<TestRegistrationComponent>(registrationServiceLifecycleHandler)
        override val isRunning: Boolean
            get() = coordinator.isRunning

        override fun start() {
            coordinator.start()
        }

        override fun stop() {
            coordinator.stop()
        }
    }

    private fun getTestContext(): LifecycleTest<TestRegistrationComponent> {
        return LifecycleTest {
            addDependency<GroupPolicyProvider>()
            addDependency<ConfigurationReadService>()
            addDependency<HSMRegistrationClient>()
            addDependency(subName)

            val staticMemberRegistrationService = StaticMemberRegistrationService(
                groupPolicyProvider,
                publisherFactory,
                subscriptionFactory,
                mock(),
                mock(),
                configReadService,
                coordinatorFactory,
                hsmRegistrationClient,
                memberInfoFactory,
                mock(),
                mock(),
                membershipSchemaValidatorFactory,
                mock(),
                platformInfoProvider,
                mock(),
                virtualNodeInfoReadService,
                mock(),
            )

            val handle = RegistrationServiceLifecycleHandler(staticMemberRegistrationService)

            TestRegistrationComponent(coordinatorFactory, handle)
        }
    }
}
