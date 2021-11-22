package net.corda.virtualnode

import net.corda.configuration.read.ConfigKeys.Companion.MESSAGING_KEY
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.CPI
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.component.ServiceException
import net.corda.virtualnode.impl.VirtualNodeInfoProcessorImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import java.util.UUID

class VirtualNodeInfoProcessorConfigTest {
    private lateinit var processor: VirtualNodeInfoProcessorImpl
    private lateinit var listener: ListenerForTest
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo> =
        mock()

    private val secureHash = SecureHash("algorithm", "1".toByteArray())

    /**
     * NOTE:  service.config is NOT set - that's the point of these tests.
     */
    @BeforeEach
    fun beforeEach() {
        listener = ListenerForTest()
        processor = VirtualNodeInfoProcessorImpl(subscriptionFactory)

        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any< CompactedProcessor<net.corda.data.identity.HoldingIdentity, net.corda.data.virtualnode.VirtualNodeInfo>>(),
                any()
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()
    }

    private fun sendOnNextRandomMessage(): HoldingIdentity {
        val holdingIdentity = HoldingIdentity("abc", UUID.randomUUID().toString())
        val newVirtualNodeInfo = VirtualNodeInfo(holdingIdentity, CPI.Identifier.newInstance("ghi", "hjk", secureHash))
        processor.onNext(Record("", holdingIdentity.toAvro(), newVirtualNodeInfo.toAvro()), null, emptyMap())
        return holdingIdentity
    }

    @Test
    fun `no config set throws if method is called`() {
        processor.registerCallback(listener)
        processor.start()
        assertThat(processor.isRunning).isTrue
        assertThrows<ServiceException> { processor.getById("should throw because we're not started") }
    }

    @Test
    fun `callback is retained if we change config`() {
        processor.registerCallback(listener)

        processor.onNewConfiguration(setOf(MESSAGING_KEY), mapOf(MESSAGING_KEY to SmartConfigImpl.empty()) )
        processor.start()

        sendOnNextRandomMessage()
        sendOnNextRandomMessage()
        assertThat(listener.lastSnapshot.size).isEqualTo(2)

        processor.stop()
        processor.onNewConfiguration(setOf(MESSAGING_KEY), mapOf(MESSAGING_KEY to SmartConfigImpl.empty()) )
        processor.start()

        sendOnNextRandomMessage()
        sendOnNextRandomMessage()
        assertThat(listener.lastSnapshot.size).isEqualTo(4)
    }

    @Test
    fun `can restart service`() {
        processor.registerCallback(listener)


        processor.onNewConfiguration(setOf(MESSAGING_KEY), mapOf(MESSAGING_KEY to SmartConfigImpl.empty()) )
        processor.start()
        processor.stop()
        processor.start()
    }

    @Test
    fun `can stop unconfigured service`() {
        processor.stop()
    }
}
