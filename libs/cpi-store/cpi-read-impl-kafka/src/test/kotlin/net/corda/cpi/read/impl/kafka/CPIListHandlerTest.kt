package net.corda.cpi.read.impl.kafka

import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIListener
import net.corda.data.packaging.CPIIdentifier
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

class CPIListHandlerTest {
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<CPIIdentifier, CPIMetadata> = mock()
    private lateinit var cpiListHandler: CPIListHandler
    private val signersHashA = "SHA256:6a838d1e427f4389aa3989d4372672c36e7843d8a3489b59439f09f23891e901"
    private val signersHashB = "SHA256:12add1e427f4389aa337838764873989d4372672c36e7843d8a3489b53433443"

    @BeforeEach
    fun beforeEach() {
        val config = ConfigFactory.empty()
        cpiListHandler = CPIListHandler(subscriptionFactory, ConfigFactory.empty())
        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig("CONFIGURATION_READ_SERVICE", "default-topic"),
                cpiListHandler,
                config
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()
    }

    @Test
    fun `register callback then call onSnapshot`() {
        val cpbIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA)
        val cpbMetadata: CPIMetadata = CPIMetadata(mapOf("metaName1" to "metaValue1", "metaName2" to "metaValue2"))
        val listener = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpbIdentifier to cpbMetadata)
        cpiListHandler.start()
        cpiListHandler.registerCPIListCallback(listener)
        cpiListHandler.onSnapshot(inputCpbIdentifier)
        assertTrue(listener.update!!)
        assertIdentities(inputCpbIdentifier, listener.snapshot!!)
    }

    // TODO: Switch this to the proper comparison
    private fun assertIdentities(expected: Map<CPIIdentifier, CPIMetadata>, actual: Map<Cpb.Identifier, Cpb.MetaData>) {
        expected.forEach {
            assertEquals(it.key.signersHash, it.key.signersHash)
            //assertEquals(expected.values, actual.values)

            expected.values.forEachIndexed { index, cpiMetadata ->
                val actualMetadata = actual.values.elementAt(index)
                assertEquals(cpiMetadata.metadataMap, actualMetadata.metadata)
            }
        }
    }

    @Test
    fun `register callback then call onNext`() {
        val cpbMetadataA: CPIMetadata = CPIMetadata(mapOf("metaNameA1" to "metaValueA1", "metaNameA2" to "metaValueA2"))
        val cpbMetadataB: CPIMetadata = CPIMetadata(mapOf("metaNameB1" to "metaValueB1", "metaNameB2" to "metaValueB2"))
        val cpbIdentifierA: CPIIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA)
        val cpbIdentifierB: CPIIdentifier = CPIIdentifier("CPI2", "2.0", signersHashB)
        val listener = CPIListenerImplTest()
        val cpbIdentifiers = mapOf(cpbIdentifierA to cpbMetadataA, cpbIdentifierB to cpbMetadataB)
        cpiListHandler.start()
        cpiListHandler.registerCPIListCallback(listener)
        cpiListHandler.onNext(Record(CPIReadImplKafka.CPIMETADATA_TOPICNAME,
            cpbIdentifierA, cpbMetadataB), cpbMetadataA, cpbIdentifiers)
        assertTrue(listener.update!!)
        assertIdentities(cpbIdentifiers, listener.snapshot!!)
    }

    @Test
    fun `listener not invoked after unregister`() {
        val cpbMetadata: CPIMetadata = CPIMetadata(mapOf("metaName1" to "metaValue1", "metaName2" to "metaValue2"))
        val cpbIdentifier: CPIIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA)
        val listener = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpbIdentifier to cpbMetadata)
        cpiListHandler.start()
        val handle = cpiListHandler.registerCPIListCallback(listener)
        cpiListHandler.onSnapshot(inputCpbIdentifier)
        assertTrue(listener.update!!)
        assertIdentities(inputCpbIdentifier, listener.snapshot!!)
        listener.update = null
        listener.snapshot = null
        handle.close()
        cpiListHandler.onSnapshot(inputCpbIdentifier)
        assertNull(listener.update)
        assertNull(listener.snapshot)
    }

    @Test
    fun `register callback twice then call onSnapshot`() {
        val cpbMetadata: CPIMetadata = CPIMetadata(mapOf("metaName1" to "metaValue1", "metaName2" to "metaValue2"))
        val cpbIdentifier: CPIIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA)
        val listenerA = CPIListenerImplTest()
        val listenerB = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpbIdentifier to cpbMetadata)
        cpiListHandler.start()
        cpiListHandler.registerCPIListCallback(listenerA)
        cpiListHandler.registerCPIListCallback(listenerB)
        cpiListHandler.onSnapshot(inputCpbIdentifier)
        assertTrue(listenerA.update!!)
        assertIdentities(inputCpbIdentifier, listenerA.snapshot!!)
        assertTrue(listenerB.update!!)
        assertIdentities(inputCpbIdentifier, listenerB.snapshot!!)
    }
}

class CPIListenerImplTest: CPIListener {
    var update: Boolean? = null
    var snapshot: Map<Cpb.Identifier, Cpb.MetaData>? = null
    var changedKeys: Set<Cpb.Identifier>? = null
    override fun onUpdate(changedKeys: Set<Cpb.Identifier>, currentSnapshot: Map<Cpb.Identifier, Cpb.MetaData>) {
        update = true
        this.changedKeys = changedKeys
        snapshot = currentSnapshot
    }
}


