package net.corda.ledger.utxo.flow.impl.transaction

import net.corda.crypto.impl.CompositeKeyProviderImpl
import net.corda.crypto.impl.keys
import net.corda.internal.serialization.deserialize
import net.corda.ledger.utxo.test.UtxoLedgerTest
import net.corda.v5.crypto.KeyUtils
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The below test exercises `KeyUtils` and `CompositeKeyImpl` in case `key` and `otherKeys` are
// `BCECPublicKey` and non `BCECPublicKey` (in which case <BCECPublicKey>.hashCode != <non BCECPublicKey>.hashCode
// but <BCECPublicKey>.equals == <non BCECPublicKey>.equals)
internal class KeyUtilsTests : UtxoLedgerTest() {
    private val kpg: KeyPairGenerator =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
    private val publicKey1 = kpg.generateKeyPair().public.also { println(it) }
    private val publicKey2 = kpg.generateKeyPair().public.also { println(it) }
    private val compositeKey =
        CompositeKeyProviderImpl().createFromKeys(listOf(publicKey1, publicKey2), 1)
            .also { println("Composite key: $it") }
    private val serializedCompositeKey = serializationServiceNullCfg.serialize(compositeKey)
    private val deserializedCompositeKey = serializationServiceNullCfg.deserialize(serializedCompositeKey)
        .also { println("Deserialized composite key: $it") }
    private val publicKeys = setOf(publicKey1, publicKey2)
    private val bcEcPublicKeys = deserializedCompositeKey.keys

    @Test
    fun `Test isKeyInSet on original`() {
        assertTrue(KeyUtils.isKeyInSet(compositeKey, publicKeys))
    }

    @Test
    fun `Test isKeyFulfilledBy on original`() {
        assertTrue(KeyUtils.isKeyFulfilledBy(compositeKey, publicKeys))
    }

    @Test
    fun `Test equality`() {
        assertEquals(compositeKey, deserializedCompositeKey)
    }

    @Test
    fun `Test isKeyInSet on deserialized`() {
        assertTrue(KeyUtils.isKeyInSet(deserializedCompositeKey, publicKeys))
    }

    @Test
    fun `Test isKeyFulfilledBy on deserialized`() {
        assertTrue(KeyUtils.isKeyFulfilledBy(deserializedCompositeKey, publicKeys))
    }

    @Test
    fun `Test isKeyInSet on deserialized reversed`() {
        assertTrue(KeyUtils.isKeyInSet(compositeKey, bcEcPublicKeys))
    }

    @Test
    fun `Test isKeyFulfilledBy on deserialized reversed`() {
        assertTrue(KeyUtils.isKeyFulfilledBy(compositeKey, bcEcPublicKeys))
    }
}