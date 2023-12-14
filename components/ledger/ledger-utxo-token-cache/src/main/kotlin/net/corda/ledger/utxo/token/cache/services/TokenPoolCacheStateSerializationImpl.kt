package net.corda.ledger.utxo.token.cache.services

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState

class TokenPoolCacheStateSerializationImpl(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : TokenPoolCacheStateSerialization {

    private val cordaSerializer = cordaAvroSerializationFactory.createAvroSerializer<TokenPoolCacheState> {}
    private val cordaDeserializer =
        cordaAvroSerializationFactory.createAvroDeserializer({}, TokenPoolCacheState::class.java)

    override fun serialize(state: TokenPoolCacheState): ByteArray {
        return checkNotNull(cordaSerializer.serialize(state))
    }

    override fun deserialize(bytes: ByteArray): TokenPoolCacheState {
        return checkNotNull(cordaDeserializer.deserialize(bytes))
    }
}
