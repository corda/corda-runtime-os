package net.corda.crypto.service.impl

import net.corda.crypto.component.persistence.CachedSoftKeysRecord
import net.corda.crypto.component.persistence.KeyValueMutator
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SigningKeysPersistenceProvider
import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.persistence.inmemory.InMemoryKeyValuePersistence
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import java.security.KeyPair
import java.security.KeyPairGenerator
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