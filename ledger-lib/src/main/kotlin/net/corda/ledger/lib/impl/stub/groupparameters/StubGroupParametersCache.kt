package net.corda.ledger.lib.impl.stub.groupparameters

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.ledger.lib.common.Constants.CACHE_MAX_SIZE
import net.corda.ledger.utxo.flow.impl.persistence.GroupParametersCache
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.crypto.SecureHash

class StubGroupParametersCache : GroupParametersCache {

    private val cache: Cache<SecureHash, SignedGroupParameters> = CacheFactoryImpl().build(
        "Group-parameters-cache",
        Caffeine.newBuilder().maximumSize(CACHE_MAX_SIZE)
    )

    override fun get(hash: SecureHash): SignedGroupParameters? {
        return cache.getIfPresent(hash)
    }

    override fun put(groupParameters: SignedGroupParameters) {
        cache.put(groupParameters.hash, groupParameters)
    }
}