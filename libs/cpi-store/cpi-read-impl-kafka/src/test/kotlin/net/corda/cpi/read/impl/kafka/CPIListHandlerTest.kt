package net.corda.cpi.read.impl.kafka

import com.typesafe.config.ConfigFactory
import net.corda.cpi.read.CPIListener
import net.corda.cpi.utils.CPI_LIST_TOPIC_NAME
import net.corda.cpi.utils.CPI_SUBSCRIPTION_GROUP_NAME
import net.corda.cpi.utils.toSerializedString
import net.corda.data.packaging.CPIMetadata
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest
import net.corda.packaging.ManifestCordappInfo
import net.corda.packaging.converters.toAvro
import net.corda.packaging.converters.toCorda
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import java.util.*


class CPIListHandlerTest {
    private val random = Random(0)
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val subscription: CompactedSubscription<String, CPIMetadata> = mock()
    private lateinit var cpiListHandler: CPIListHandler

    private val cpkTypeA = CPK.Type.CORDA_API

    private fun createCPIId(key: String): CPI.Identifier {
        return CPI.Identifier.newInstance("SomeName${key}",
            "1.0",
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)))
    }

    private fun createCPIMetadata(id: CPI.Identifier, key: String): CPI.Metadata {
        val cpkId = CPK.Identifier.newInstance("SomeName${key}",
            "1.0", SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)))
        val cordappManifest = CordappManifest(
            "net.corda.Bundle$key",
            "1.2.3",
            12,
            34,
            ManifestCordappInfo("someName$key", "R3", 42, "some license"),
            ManifestCordappInfo("someName$key", "R3", 42, "some license"),
            mapOf("Corda-Contract-Classes" to "contractClass1, contractClass2",
                "Corda-Flow-Classes" to "flowClass1, flowClass2"),
        )
        val cpkManifest = CPK.Manifest.newInstance(CPK.FormatVersion.newInstance(2, 3))
        val cpkMetadata = CPK.Metadata.newInstance(cpkManifest,
            "mainBundleA.jar",
            listOf("libraryA.jar"),
            sequenceOf(cpkId).toCollection(TreeSet()),
            cordappManifest,
            cpkTypeA,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            emptySet()
        )
        return CPI.Metadata.newInstance(
            id,
            SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes)),
            listOf(cpkMetadata),
            "someString")
    }

    @BeforeEach
    fun beforeEach() {
        val config = SmartConfigImpl.empty()
        cpiListHandler = CPIListHandler(subscriptionFactory, SmartConfigImpl.empty())
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
        val cpiIdentifier = createCPIId("A")
        val cpiMetadata = createCPIMetadata(cpiIdentifier, "A")
        val avroCPIMetadata = cpiMetadata.toAvro()
        val listener = CPIListenerImplTest()
        val inputCpbIdentifier = mapOf(cpiIdentifier.toSerializedString() to avroCPIMetadata)
        cpiListHandler.start()
        cpiListHandler.registerCPIListCallback(listener)
        cpiListHandler.onSnapshot(inputCpbIdentifier)
        assertTrue(listener.update!!)
        assertIdentities(inputCpbIdentifier, listener.snapshot!!)
    }

    private fun assertIdentities(expected: Map<String, CPIMetadata>, actual: Map<CPI.Identifier, CPI.Metadata>) {
        assertEquals(expected.size, actual.size)
        expected.keys.forEach {
            val identifier = expected[it]?.id?.toCorda()
            assertTrue(actual.containsKey(identifier))
            val expectedAvroMetadata = expected[it]!!
            val metadata = actual[identifier]!!
            val expectedMetadata = expectedAvroMetadata.toCorda()
            assertEquals(expectedMetadata.id, metadata.id)
            assertEquals(expectedMetadata.hash, metadata.hash)
            assertEquals(expectedMetadata.networkPolicy, metadata.networkPolicy)
            expectedMetadata.cpks.containsAll(metadata.cpks)
            metadata.cpks.containsAll(expectedMetadata.cpks)
        }
    }

    @Test
    fun `register callback then call onNext`() {
        val cpiIdentifierA = createCPIId("A")
        val cpiMetadataA = createCPIMetadata(cpiIdentifierA, "A")
        val cpiIdentifierB = createCPIId("B")
        val cpiMetadataB = createCPIMetadata(cpiIdentifierB, "B")

        val cpbMetadataA = cpiMetadataA.toAvro()
        val cpbMetadataB = cpiMetadataB.toAvro()
        val cpiIdentifierStrA = cpiIdentifierA.toSerializedString()
        val cpiIdentifierStrB = cpiIdentifierB.toSerializedString()

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
        val cpiIdentifier = createCPIId("A")
        val cpiMetadata = createCPIMetadata(cpiIdentifier, "A")

        val avroCPBMetadata = cpiMetadata.toAvro()
        val cpiIdentifierStr = cpiIdentifier.toSerializedString()
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
        val cpiIdentifier = createCPIId("A")
        val cpiMetadata = createCPIMetadata(cpiIdentifier, "A")
        val avroCPBMetadata = cpiMetadata.toAvro()
        val cpiIdentifierStr = cpiIdentifier.toSerializedString()
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


