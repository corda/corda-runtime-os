package net.corda.crypto.impl.dev

import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.KeyValueMutator
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import java.util.concurrent.ConcurrentHashMap

class InMemoryKeyValuePersistenceFactory : KeyValuePersistenceFactory {

    val signingData =
        ConcurrentHashMap<String, Pair<SigningPersistentKeyInfo?, SigningPersistentKeyInfo>>()

    val cryptoData =
        ConcurrentHashMap<String, Pair<DefaultCryptoCachedKeyInfo?, DefaultCryptoPersistentKeyInfo>>()

    override fun createSigningPersistence(
        memberId: String,
        mutator: KeyValueMutator<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    ): KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo> {
        return InMemoryKeyValuePersistence(
            memberId = memberId,
            mutator = mutator,
            data = signingData
        )
    }

    override fun createDefaultCryptoPersistence(
        memberId: String,
        mutator: KeyValueMutator<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
    ): KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo> {
        return InMemoryKeyValuePersistence(
            memberId = memberId,
            mutator = mutator,
            data = cryptoData
        )
    }
}