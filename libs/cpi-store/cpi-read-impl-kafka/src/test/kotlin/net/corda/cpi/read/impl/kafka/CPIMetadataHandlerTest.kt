package net.corda.cpi.read.impl.kafka

import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIMetadataListener
import net.corda.data.packaging.CPIMetadata
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.packaging.Cpb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class CPIMetadataHandlerTest {
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<String, CPIMetadata> = mock()
    private lateinit var cpiMetadataHandler: CPIMetadataHandler

    @BeforeEach
    fun beforeEach() {
        val config = ConfigFactory.empty()
        cpiMetadataHandler = CPIMetadataHandler(subscriptionFactory, ConfigFactory.empty())
        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CPIReadImplKafka.CPIMETADATA_READ, CPIReadImplKafka.CPIMETADATA_TOPICNAME),
                cpiMetadataHandler,
                config
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()
    }

    @Test
    fun `register callback then call onSnapshot`() {
        val cpbMetadata: CPIMetadata = mock()
        val listener = CPIMetadataImplTest()
        val inputMetadata = mapOf("ID" to cpbMetadata)
        cpiMetadataHandler.start()
        cpiMetadataHandler.registerCPIMetadataCallback(listener)
        cpiMetadataHandler.onSnapshot(inputMetadata)
        assertTrue(listener.update!!)
        assertEquals(inputMetadata, listener.snapshot)
    }

    @Test
    fun `register callback then call onNext`() {
        val cpbMetadataOld: CPIMetadata = mock()
        val cpbMetadataNew: CPIMetadata = mock()
        val listener = CPIMetadataImplTest()
        val inputMetadata = mapOf("ID" to cpbMetadataNew)
        cpiMetadataHandler.start()
        cpiMetadataHandler.registerCPIMetadataCallback(listener)
        // cpiMetadataHandler.onSnapshot(inputMetadata)
        cpiMetadataHandler.onNext(Record(CPIReadImplKafka.CPIMETADATA_TOPICNAME, "ID", cpbMetadataNew),
                                  cpbMetadataOld, inputMetadata)
        assertTrue(listener.update!!)
        assertEquals(inputMetadata, listener.snapshot)
    }

    @Test
    fun `listener not invoked after unregister`() {
        val cpbMetadataA: CPIMetadata = mock()
        val cpbMetadataB: CPIMetadata = mock()
        val listener = CPIMetadataImplTest()
        val inputMetadataA = mapOf<String, CPIMetadata>("ID1" to cpbMetadataA)
        val inputMetadataB = mapOf<String, CPIMetadata>("ID1" to cpbMetadataA, "ID2" to cpbMetadataB)
        cpiMetadataHandler.start()
        val handle = cpiMetadataHandler.registerCPIMetadataCallback(listener)
        cpiMetadataHandler.onSnapshot(inputMetadataA)
        assertTrue(listener.update!!)
        assertEquals(inputMetadataA, listener.snapshot)
        listener.update = null
        listener.snapshot = null
        handle.close()
        cpiMetadataHandler.onSnapshot(inputMetadataB)
        assertNull(listener.update)
        assertNull(listener.snapshot)
    }

    @Test
    fun `register callback twice then call onSnapshot`() {
        val cpbMetadataA: CPIMetadata = mock()
        val listenerA = CPIMetadataImplTest()
        val listenerB = CPIMetadataImplTest()
        val inputMetadata = mapOf<String, CPIMetadata>("ID1" to cpbMetadataA)
        cpiMetadataHandler.start()
        cpiMetadataHandler.registerCPIMetadataCallback(listenerA)
        cpiMetadataHandler.registerCPIMetadataCallback(listenerB)
        cpiMetadataHandler.onSnapshot(inputMetadata)
        assertTrue(listenerA.update!!)
        assertEquals(inputMetadata, listenerA.snapshot)
        assertTrue(listenerB.update!!)
        assertEquals(inputMetadata, listenerB.snapshot)
    }
}

class CPIMetadataImplTest: CPIMetadataListener {
    var update: Boolean? = null
    var snapshot: Map<String, Cpb.MetaData>? = null
    var changedKeys: Set<String>? = null

    override fun onUpdate(changedKeys: Set<String>, currentSnapshot: Map<String, Cpb.MetaData>) {
        update = true
        this.changedKeys = changedKeys
        this.snapshot = currentSnapshot
    }
}