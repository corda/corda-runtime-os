package net.corda.cpi.read.impl.kafka

import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIListener
import net.corda.cpi.utils.CPI_LIST_TOPIC_NAME
import net.corda.cpi.utils.CPI_SUBSCRIPTION_GROUP_NAME
import net.corda.cpi.utils.toSerializedString
import net.corda.data.packaging.CPIIdentifier
import net.corda.data.packaging.CPIIdentity
import net.corda.data.packaging.CPIMetadata
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.packaging.CPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class CPIListHandlerTest {
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<String, CPIMetadata> = mock()
    private lateinit var cpiListHandler: CPIListHandler
    private val signersHashA = "SHA256:6a838d1e427f4389aa3989d4372672c36e7843d8a3489b59439f09f23891e901"
    private val signersHashB = "SHA256:12add1e427f4389aa337838764873989d4372672c36e7843d8a3489b53433443"

    @BeforeEach
    fun beforeEach() {
        val config = ConfigFactory.empty()
        cpiListHandler = CPIListHandler(subscriptionFactory, ConfigFactory.empty())
        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(CPI_SUBSCRIPTION_GROUP_NAME,
                                   CPI_LIST_TOPIC_NAME),
                cpiListHandler,
                config
            )
        ).thenReturn(subscription)

        Mockito.doNothing().`when`(subscription).start()
        Mockito.doNothing().`when`(subscription).stop()
    }

    @Test
    fun `register callback then call onSnapshot`() {
        val avroCPBIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA, CPIIdentity("cn=Rosanna Lee, ou=People, o=Sun, c=us", "groupId"))
        val avroCPBMetadata = CPIMetadata(avroCPBIdentifier, "networkPolicy1", mapOf("metaName1" to "metaValue1", "metaName2" to "metaValue2"))

        val cpiIdentifier = CPI.Identifier.newInstance(avroCPBMetadata)
        val listener = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpiIdentifier.toSerializedString() to avroCPBMetadata)
        cpiListHandler.start()
        cpiListHandler.registerCPIListCallback(listener)
        cpiListHandler.onSnapshot(inputCpbIdentifier)
        assertTrue(listener.update!!)
        assertIdentities(inputCpbIdentifier, listener.snapshot!!)
    }

    // TODO: Switch this to the proper comparison
    private fun assertIdentities(expected: Map<String, CPIMetadata>, actual: Map<CPI.Identifier, CPI.Metadata>) {
        assertEquals(expected.size, actual.size)
        expected.keys.forEach {
            val identifier = CPI.Identifier.newInstance(expected[it]!!)
            assertTrue(actual.containsKey(identifier))
            val expectedAvroMetadata = expected[it]
            val metadata = actual[identifier]
            val expectedMetadata = CPI.Metadata.newInstance(expectedAvroMetadata!!)
            // TODO: Compare the CPKs as well
            assertEquals(expectedMetadata.id, metadata?.id)
            assertEquals(expectedMetadata.networkPolicy, metadata?.networkPolicy)
        }
    }

    @Test
    fun `register callback then call onNext`() {
        val avroCPBIdentifierA = CPIIdentifier("CPI1", "1.0", signersHashA, CPIIdentity("cn=Rosanna LeeA, ou=People, o=Sun, c=us", "groupIdA"))
        val avroCPBIdentifierB = CPIIdentifier("CPI2", "2.0", signersHashB, CPIIdentity("cn=Rosanna LeeB, ou=People, o=Sun, c=us", "groupIdB"))
        val cpbMetadataA = CPIMetadata(avroCPBIdentifierA, "networkPolicyA", mapOf("metaNameA1" to "metaValueA1", "metaNameA2" to "metaValueA2"))
        val cpbMetadataB = CPIMetadata(avroCPBIdentifierB, "networkPolicyB", mapOf("metaNameB1" to "metaValueB1", "metaNameB2" to "metaValueB2"))
        val cpiIdentifierStrA = CPI.Identifier.newInstance(cpbMetadataA).toSerializedString()
        val cpiIdentifierStrB = CPI.Identifier.newInstance(cpbMetadataB).toSerializedString()

        val listener = CPIListenerImplTest()
        val cpbIdentifiers = mapOf(cpiIdentifierStrA to cpbMetadataA, cpiIdentifierStrB to cpbMetadataB)
        cpiListHandler.start()
        cpiListHandler.registerCPIListCallback(listener)
        cpiListHandler.onNext(Record(CPI_LIST_TOPIC_NAME, cpiIdentifierStrA, cpbMetadataB), cpbMetadataA, cpbIdentifiers)
        assertTrue(listener.update!!)
        assertIdentities(cpbIdentifiers, listener.snapshot!!)
    }

    @Test
    fun `listener not invoked after unregister`() {
        val avroCPBIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA, CPIIdentity("cn=Rosanna Lee, ou=People, o=Sun, c=us", "groupIdA"))
        val avroCPBMetadata = CPIMetadata(avroCPBIdentifier, "networkPolicy", mapOf("metaName1" to "metaValue1", "metaName2" to "metaValue2"))
        val cpiIdentifierStr = CPI.Identifier.newInstance(avroCPBIdentifier).toSerializedString()
        val listener = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpiIdentifierStr to avroCPBMetadata)
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
        val avroCPBIdentifier = CPIIdentifier("CPI1", "1.0", signersHashA, CPIIdentity("cn=Rosanna Lee, ou=People, o=Sun, c=us", "groupIdA"))
        val avroCPBMetadata = CPIMetadata(avroCPBIdentifier, "networkPolicy", mapOf("metaName1" to "metaValue1", "metaName2" to "metaValue2"))
        val cpiIdentifierStr = CPI.Identifier.newInstance(avroCPBMetadata).toSerializedString()
        val listenerA = CPIListenerImplTest()
        val listenerB = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpiIdentifierStr to avroCPBMetadata)
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
    var snapshot: Map<CPI.Identifier, CPI.Metadata>? = null
    var changedKeys: Set<CPI.Identifier>? = null
    override fun onUpdate(changedKeys: Set<CPI.Identifier>, currentSnapshot: Map<CPI.Identifier, CPI.Metadata>) {
        update = true
        this.changedKeys = changedKeys
        snapshot = currentSnapshot
    }
}


