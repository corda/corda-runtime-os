package net.corda.ledger.common.impl.transaction

import com.esotericsoftware.kryo.Kryo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.cipher.suite.impl.DigestServiceImpl
import net.corda.crypto.merkle.impl.MerkleTreeFactoryImpl
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.sandbox.SandboxException
import net.corda.sandbox.SandboxGroup
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock

internal fun mockSandboxGroup(taggedClasses: Set<Class<*>>): SandboxGroup {
    val standardClasses = listOf(String::class.java, Class::class.java)
    return mock<SandboxGroup>().also {
        var index = 0
        val bundleClasses = (standardClasses + taggedClasses).associateBy { "${index++}" }
        val tagCaptor = argumentCaptor<Class<*>>()
        `when`(it.getStaticTag(tagCaptor.capture())).thenAnswer {
            bundleClasses.keys.firstOrNull { value -> bundleClasses[value] == tagCaptor.lastValue }?.toString()
                ?: throw SandboxException("Class ${tagCaptor.lastValue} was not loaded from any bundle.")
        }
        val classCaptor = argumentCaptor<String>()
        `when`(it.getClass(any(), classCaptor.capture())).thenAnswer {
            bundleClasses[classCaptor.lastValue]
                ?: throw SandboxException("Class ${tagCaptor.lastValue} was not loaded from any bundle.")
        }
    }

}

class WireTransactionKryoSerializationTest {
    companion object {
        private lateinit var digestService: DigestService
        private lateinit var merkleTreeFactory: MerkleTreeFactory

        @BeforeAll
        @JvmStatic
        fun setup() {
            val schemeMetadata = CipherSchemeMetadataImpl()
            digestService = DigestServiceImpl(schemeMetadata, null)
            merkleTreeFactory = MerkleTreeFactoryImpl(digestService)
        }
    }

    @Test
    fun `serialization of a Wire Tx object using the kryo default serialization`() {
        val mapper = jacksonObjectMapper()
        val transactionMetaData = TransactionMetaData(
            mapOf(
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
            )
        )
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())
        val componentGroupLists = listOf(
            listOf(mapper.writeValueAsBytes(transactionMetaData)), // CORE-5940
            listOf(".".toByteArray()),
            listOf("abc d efg".toByteArray()),
        )
        val wireTransaction = WireTransaction(
            merkleTreeFactory,
            digestService,
            privacySalt,
            componentGroupLists
        )

//        val sandboxGroup = mockSandboxGroup(setOf(WireTransaction::class.java))
        val serializer = KryoCheckpointSerializer(Kryo())
/*            DefaultKryoCustomizer.customize(
                Kryo(),
                emptyMap(),
                CordaClassResolver(sandboxGroup),
                ClassSerializer(sandboxGroup)
            )
        )
 */
        val bytes = serializer.serialize(wireTransaction)
        val deserialized = serializer.deserialize(bytes, WireTransaction::class.java)
        assertEquals(wireTransaction, deserialized)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow{
            deserialized.id
        }
        assertEquals(wireTransaction.id, deserialized.id)
    }
}