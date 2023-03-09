package net.corda.membership.impl.registration.staticnetwork

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.data.CordaAvroSerializationFactory
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
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
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
import org.mockito.kotlin.eq
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
    private val membershipGroupReaderProvider = mock<MembershipGroupReaderProvider> {
        on { getGroupReader(any()) } doReturn mock()
    }
    private val membershipQueryClient = mock<MembershipQueryClient>()
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroSerializer<Any>(any()) } doReturn mock()
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn mock()
    }

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
        cordaAvroSerializationFactory,
        membershipSchemaValidatorFactory,
        mock(),
        platformInfoProvider,
        mock(),
        virtualNodeInfoReadService,
        mock(),
        membershipGroupReaderProvider,
        membershipQueryClient,
    )

    private val registrationServiceLifecycleHandler = RegistrationServiceLifecycleHandler(
        staticMemberRegistrationService
    )

    @Test
    fun `Start event does not immediately move to UP status`() {
        getTestContext().run {
            testClass.start()

            verifyIsDown<TestRegistrationComponent>()
        }
    }

    @Test
    fun `UP is posted once all dependencies are UP and configuration has been provided`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            verifyIsUp<TestRegistrationComponent>()
            assertNotNull(testClass.registrationServiceLifecycleHandler.publisher)
            assertNotNull(testClass.registrationServiceLifecycleHandler.groupParametersCache)
        }
    }

    @Test
    fun `component remains UP if the config is changed`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            verifyIsUp<TestRegistrationComponent>()

            // A config update of any variety should not trigger the test component to go down. This can be verified by
            // sending the same thing again, as the code will still respond to this event as if it were a change.
            sendConfigUpdate<TestRegistrationComponent>(configs)
            verifyIsUp<TestRegistrationComponent>()
        }
    }

    @Test
    fun `component is DOWN after a stop event and the publisher is closed`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            verifyIsUp<TestRegistrationComponent>()
            testClass.stop()
            verifyIsDown<TestRegistrationComponent>()
        }

        assertThrows<IllegalArgumentException> { registrationServiceLifecycleHandler.publisher }
    }

    @Test
    fun `component goes DOWN if one of its dependencies goes DOWN`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            toggleDependency<GroupPolicyProvider>({
                verifyIsDown<TestRegistrationComponent>()
            }, {
                verifyIsDown<TestRegistrationComponent>()
            })
            sendConfigUpdate<TestRegistrationComponent>(configs)
            verifyIsUp<TestRegistrationComponent>()

            toggleDependency<ConfigurationReadService>({
                verifyIsDown<TestRegistrationComponent>()
            }, {
                verifyIsDown<TestRegistrationComponent>()
            })
            sendConfigUpdate<TestRegistrationComponent>(configs)
            verifyIsUp<TestRegistrationComponent>()

            toggleDependency<HSMRegistrationClient>({
                verifyIsDown<TestRegistrationComponent>()
            }, {
                verifyIsDown<TestRegistrationComponent>()
            })
            sendConfigUpdate<TestRegistrationComponent>(configs)
            verifyIsUp<TestRegistrationComponent>()
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if subscription goes DOWN then UP`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            verifyIsUp<TestRegistrationComponent>()

            toggleDependency(subName, {
                verifyIsDown<TestRegistrationComponent>()
            }, {
                verifyIsUp<TestRegistrationComponent>()
            })
        }
    }

    @Test
    fun `component goes DOWN and comes back UP if a dependent component errors and comes back`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)
            verifyIsUp<TestRegistrationComponent>()

            setDependencyToError<HSMRegistrationClient>()
            verifyIsDown<TestRegistrationComponent>()
            bringDependencyUp<HSMRegistrationClient>()

            // Model a config update coming back due to us re-registering with the config read service.
            sendConfigUpdate<TestRegistrationComponent>(configs)
            verifyIsUp<TestRegistrationComponent>()
        }
    }

    @Test
    fun `component handles a subscription error and restart`() {
        getTestContext().run {
            testClass.start()
            bringDependenciesUp()
            sendConfigUpdate<TestRegistrationComponent>(configs)

            verifyIsUp<TestRegistrationComponent>()

            setDependencyToError(subName)
            verifyIsDown<TestRegistrationComponent>()
            bringDependencyUp(subName)
            verifyIsUp<TestRegistrationComponent>()
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
            addDependency<MembershipQueryClient>()
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
                cordaAvroSerializationFactory,
                membershipSchemaValidatorFactory,
                mock(),
                platformInfoProvider,
                mock(),
                virtualNodeInfoReadService,
                mock(),
                membershipGroupReaderProvider,
                membershipQueryClient,
            )

            val handle = RegistrationServiceLifecycleHandler(staticMemberRegistrationService)

            TestRegistrationComponent(coordinatorFactory, handle)
        }
    }
}
