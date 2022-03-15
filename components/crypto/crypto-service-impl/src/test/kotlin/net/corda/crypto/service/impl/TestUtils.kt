package net.corda.crypto.service.impl

import net.corda.crypto.persistence.CachedSoftKeysRecord
import net.corda.crypto.persistence.EntityKeyInfo
import net.corda.crypto.persistence.KeyValueMutator
import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.crypto.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.persistence.SoftKeysPersistenceProvider
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

fun generateKeyPair(schemeMetadata: CipherSchemeMetadata, signatureSchemeName: String): KeyPair {
    val scheme = schemeMetadata.findSignatureScheme(signatureSchemeName)
    val keyPairGenerator = KeyPairGenerator.getInstance(
        scheme.algorithmName,
        schemeMetadata.providers.getValue(scheme.providerName)
    )
    if (scheme.algSpec != null) {
        keyPairGenerator.initialize(scheme.algSpec, schemeMetadata.secureRandom)
    } else if (scheme.keySize != null) {
        keyPairGenerator.initialize(scheme.keySize!!, schemeMetadata.secureRandom)
    }
    return keyPairGenerator.generateKeyPair()
}

class InMemoryKeyValuePersistence<V, E>(
    private val data: ConcurrentHashMap<String, V>,
    private val mutator: KeyValueMutator<V, E>
) : KeyValuePersistence<V, E>, AutoCloseable {

    override fun put(entity: E, vararg key: EntityKeyInfo): V {
        require(key.isNotEmpty()) {
            "There must be at least one key provided."
        }
        val value = mutator.mutate(entity)
        key.forEach {
            data[it.key] = value
        }
        return value
    }

    override fun get(key: String): V? =
        data[key]

    override fun close() {
        data.clear()
    }
}

class TestSigningKeysPersistenceProvider : SigningKeysPersistenceProvider {
    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<SigningKeysRecord, SigningKeysRecord>>()

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<SigningKeysRecord, SigningKeysRecord>
    ): KeyValuePersistence<SigningKeysRecord, SigningKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, SigningKeysRecord>()
            )
        }

    override val isRunning: Boolean = true

    override fun start() {
    }

    override fun stop() {
    }
}

class TestSoftKeysPersistenceProvider : SoftKeysPersistenceProvider {
    companion object {
        private val logger = contextLogger()
    }
    private val instances =
        ConcurrentHashMap<String, InMemoryKeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord>>()

    override fun getInstance(
        tenantId: String,
        mutator: KeyValueMutator<CachedSoftKeysRecord, SoftKeysRecord>
    ): KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord> =
        instances.computeIfAbsent(tenantId) {
            InMemoryKeyValuePersistence(
                mutator = mutator,
                data = ConcurrentHashMap<String, CachedSoftKeysRecord>()
            )
        }

    override val isRunning: Boolean = true

    override fun start() {
    }

    override fun stop() {
    }
}

fun assertThatIsBetween(actual: Instant, before: Instant, after: Instant) {
    MatcherAssert.assertThat(
        actual.toEpochMilli(),
        Matchers.allOf(
            Matchers.greaterThanOrEqualTo(before.toEpochMilli()),
            Matchers.lessThanOrEqualTo(after.toEpochMilli())
        )
    )
}

inline fun <reified RESULT: Any> act(block: () -> RESULT?): ActResult<RESULT> {
    val before = Instant.now()
    val result = block()
    val after = Instant.now()
    return ActResult(
        before = before,
        after = after,
        value = result
    )
}

open class ActResultTimestamps(
    val before: Instant,
    val after: Instant,
) {
    fun assertThatIsBetween(timestamp: Instant) = assertThatIsBetween(timestamp, before, after)
}

class ActResult<RESULT>(
    before: Instant,
    after: Instant,
    val value: RESULT?
) : ActResultTimestamps(before, after)