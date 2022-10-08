package net.corda.crypto.impl.cipher.suite

import net.corda.v5.cipher.suite.KeySchemeInfo
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.KeyScheme
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.PublicKey
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class KeySchemeMap {
    private val lock: ReadWriteLock = ReentrantReadWriteLock()

    private val keySchemeAlgorithmMap = mutableMapOf<AlgorithmIdentifier, KeyScheme>()

    private val keySchemeNameMap = mutableMapOf<String, KeySchemeInfo>()

    fun add(keyScheme: KeySchemeInfo) = lock.writeLock().withLock {
        keySchemeNameMap[keyScheme.scheme.codeName] = keyScheme
        keyScheme.scheme.algorithmOIDs.forEach {
            keySchemeAlgorithmMap[it] = keyScheme.scheme
        }
    }

    fun all(): List<KeySchemeInfo> = lock.readLock().withLock {
        keySchemeNameMap.values.toList()
    }

    fun findKeyScheme(codeName: String): KeyScheme? = lock.readLock().withLock {
        keySchemeNameMap[codeName]?.scheme
    }

    fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme? = lock.readLock().withLock {
        keySchemeAlgorithmMap[normaliseAlgorithmIdentifier(algorithm)]
    }

    fun findKeyScheme(publicKey: PublicKey, encodingHandlers: List<KeyEncodingHandler>): KeyScheme? {
        var algorithm = quickGetAlgorithmIdentifier(publicKey)
        if(algorithm != null) {
            return findKeyScheme(algorithm)
        }
        for(handler in encodingHandlers) {
            algorithm = try {
                handler.getAlgorithmIdentifier(publicKey)
            } catch (e: Throwable) {
                null
            }
            if(algorithm != null) {
                return findKeyScheme(algorithm)
            }
        }
        return null
    }

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }

    private fun quickGetAlgorithmIdentifier(publicKey: PublicKey): AlgorithmIdentifier? = try {
        SubjectPublicKeyInfo.getInstance(publicKey.encoded).algorithm
    } catch (e: Throwable) {
        null
    }
}