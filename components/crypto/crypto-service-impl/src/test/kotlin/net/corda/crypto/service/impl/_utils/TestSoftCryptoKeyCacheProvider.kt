package net.corda.crypto.service.impl._utils

import net.corda.crypto.persistence.SoftCryptoKeyCache
import net.corda.crypto.persistence.SoftCryptoKeyCacheActions
import net.corda.crypto.persistence.SoftCryptoKeyCacheProvider
import net.corda.crypto.persistence.WrappingKey
import java.util.concurrent.ConcurrentHashMap

class TestSoftCryptoKeyCacheProvider : SoftCryptoKeyCacheProvider {
    private val instance = TestSoftCryptoKeyCache()

    override fun getInstance(passphrase: String?, salt: String?): SoftCryptoKeyCache {
        check(isRunning) {
            "The provider is in invalid state."
        }
        return instance
    }

    override var isRunning: Boolean = false
        private set

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }
}

class TestSoftCryptoKeyCache : SoftCryptoKeyCache {
    private val keys = ConcurrentHashMap<String, WrappingKey>()
    override fun act(): SoftCryptoKeyCacheActions = TestSoftCryptoKeyCacheActions(keys)
}

class TestSoftCryptoKeyCacheActions(
    private val keys: MutableMap<String, WrappingKey>
) : SoftCryptoKeyCacheActions {
    override fun saveWrappingKey(alias: String, key: WrappingKey) {
        if(keys.putIfAbsent(alias, key) != null) {
            throw IllegalArgumentException("The key $alias already exists.")
        }
    }
    override fun findWrappingKey(alias: String): WrappingKey? = keys[alias]
    override fun close() = Unit
}
