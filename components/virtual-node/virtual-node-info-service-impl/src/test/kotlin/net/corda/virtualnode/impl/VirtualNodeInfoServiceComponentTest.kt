package net.corda.virtualnode.impl

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigKeys.Companion.MESSAGING_KEY
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.ListenerForTest
import net.corda.virtualnode.component.ServiceException
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

internal class VirtualNodeInfoServiceComponentTest {
    private val registry = LifecycleRegistryImpl()
    private lateinit var component: VirtualNodeInfoServiceComponentImpl
    private lateinit var processor: VirtualNodeInfoProcessorImpl

    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<HoldingIdentity, VirtualNodeInfo> = mock()
    private val configurationReadService: ConfigurationReadService = mock()
    private val coordinatorFactory: LifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(registry)

    @BeforeEach
    fun beforeEach() {
        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<HoldingIdentity, VirtualNodeInfo>>(),
                any()
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()

        processor = VirtualNodeInfoProcessorImpl(subscriptionFactory)

        component = VirtualNodeInfoServiceComponentImpl(
            coordinatorFactory,
            processor,
            configurationReadService
        )
    }

    @Test
    fun `can start component`() {
        component.start()
        assertThat(component.isRunning).isTrue
    }

    @Test
    fun `can stop component`() {
        component.start()
        assertThat(component.isRunning).isTrue

        component.stop()
        assertThat(component.isRunning).isFalse
    }

    @Test
    fun `can register callback`() {
        val listener = ListenerForTest()
        component.registerCallback(listener)
        component.start()
        assertThat(component.isRunning).isTrue

        component.stop()
        assertThat(component.isRunning).isFalse
    }

    @Test
    fun `component activates fully on service up and config message`() {
        val listener = ListenerForTest()
        component.registerCallback(listener)
        component.start()
        assertThat(component.isRunning).isTrue

        assertThrows<ServiceException> { component.getById("this should throw because we haven't started fully") }

        // Can't mock the reified createCoordinator method to return a coordinator we construct here
        // as far I can tell, so get it this way.
        // Technically we don't need this bit of code for this specific test since we're not testing the callback
        // containing the virtual node info.
        val coordinator =
            registry.getCoordinator(LifecycleCoordinatorName("net.corda.virtualnode.VirtualNodeInfoService"))
        coordinator.postEvent(
            RegistrationStatusChangeEvent(
                registration = mock(),
                status = LifecycleStatus.UP
            )
        )

        assertThrows<ServiceException> { component.getById("this should throw because we haven't started fully") }

        component.onNewConfiguration(
            setOf("foo"),
            mapOf("x" to SmartConfigImpl.empty())
        )

        assertThrows<ServiceException> { component.getById("this should throw because we haven't started fully") }

        component.onNewConfiguration(
            setOf(MESSAGING_KEY),
            mapOf(MESSAGING_KEY to SmartConfigImpl(ConfigFactory.parseString("""bootstrap.servers=localhost":"9092""")))
        )

        val holdingIdentities = component.getById("should return null list")
        assertThat(holdingIdentities).isNull()

        component.stop()
        assertThat(component.isRunning).isFalse
    }

    @Test
    fun `posting messages populates map in callback`() {
        val listener = ListenerForTest()
        component.registerCallback(listener)
        component.start()

        val coordinator =
            registry.getCoordinator(LifecycleCoordinatorName("net.corda.virtualnode.VirtualNodeInfoService"))

        // This triggers the component into registering the configuration callback.
        coordinator.postEvent(
            RegistrationStatusChangeEvent(
                registration = mock(),
                status = LifecycleStatus.UP
            )
        )

        // Now "send" a configuration
        component.onNewConfiguration(
            setOf(MESSAGING_KEY),
            mapOf(MESSAGING_KEY to SmartConfigImpl(ConfigFactory.parseString("""bootstrap.servers=localhost":"9092""")))
        )

        assertThat(listener.update).isFalse
        processor.onSnapshot(emptyMap())

        val holdingIdentity = net.corda.virtualnode.HoldingIdentity("x500", "groupId")
        val virtualNodeInfo =
            net.corda.virtualnode.VirtualNodeInfo(
                holdingIdentity,
                CPI.Identifier.newInstance("name", "version", SecureHash("algorithm", "1".toByteArray()))
            )

        processor.onNext(Record("", holdingIdentity.toAvro(), virtualNodeInfo.toAvro()), null, emptyMap())

        assertThat(listener.update).isTrue
        assertThat(component.getById(holdingIdentity.id)).isNotNull
        assertThat(component.getById(holdingIdentity.id)?.first()?.holdingIdentity).isEqualTo(holdingIdentity)


        component.stop()
        assertThat(component.isRunning).isFalse
    }
}
