package net.corda.crypto.service.impl.infra

import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SigningKeyCacheProvider
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.alias
import net.corda.crypto.persistence.category
import net.corda.crypto.persistence.createdAfter
import net.corda.crypto.persistence.createdBefore
import net.corda.crypto.persistence.masterKeyAlias
import net.corda.crypto.persistence.schemeCodeName
import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class TestSigningKeyCacheProvider(
    coordinatorFactory: LifecycleCoordinatorFactory
) : SigningKeyCacheProvider {
    private val instance = TestSigningKeyCache()

    val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<SigningKeyCacheProvider>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun getInstance(): SigningKeyCache {
        check(isRunning) {
            "The provider is in invalid state."
        }
        return instance
    }
}

class TestSigningKeyCache : SigningKeyCache {
    private val actions = ConcurrentHashMap<String, SigningKeyCacheActions>()
    override fun act(tenantId: String): SigningKeyCacheActions =
        actions.computeIfAbsent(tenantId) { TestSigningKeyCacheActions(tenantId) }

    override fun close() = Unit
}

class TestSigningKeyCacheActions(
    private val tenantId: String
) : SigningKeyCacheActions {
    private val keys = ConcurrentHashMap<String, SigningCachedKey>()

    override fun save(context: SigningKeySaveContext) {
        val now = Instant.now()
        val record = when (context) {
            is SigningPublicKeySaveContext -> {
                val encodedKey = context.key.publicKey.encoded
                SigningCachedKey(
                    id = publicKeyIdFromBytes(encodedKey),
                    tenantId = tenantId,
                    category = context.category,
                    alias = context.alias,
                    hsmAlias = context.key.hsmAlias,
                    publicKey = encodedKey,
                    keyMaterial = null,
                    schemeCodeName = context.signatureScheme.codeName,
                    masterKeyAlias = null,
                    externalId = null,
                    encodingVersion = null,
                    created = now
                )
            }
            is SigningWrappedKeySaveContext -> {
                val encodedKey = context.key.publicKey.encoded
                SigningCachedKey(
                    id = publicKeyIdFromBytes(encodedKey),
                    tenantId = tenantId,
                    category = context.category,
                    alias = context.alias,
                    hsmAlias = null,
                    publicKey = encodedKey,
                    keyMaterial = context.key.keyMaterial,
                    schemeCodeName = context.signatureScheme.codeName,
                    masterKeyAlias = context.masterKeyAlias,
                    externalId = context.externalId,
                    encodingVersion = context.key.encodingVersion,
                    created = now
                )
            }
            else -> throw  IllegalArgumentException("Unknown type ${context::class.java.name}")
        }
        if(keys.putIfAbsent(record.id, record) != null) {
            throw IllegalArgumentException("The key ${record.id} already exists.")
        }
    }

    override fun find(alias: String): SigningCachedKey? =
        keys.values.firstOrNull { it.alias == alias }

    override fun find(publicKey: PublicKey): SigningCachedKey? =
        keys[publicKey.publicKeyId()]

    @Suppress("ComplexMethod")
    override fun lookup(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningCachedKey> {
        val map = SigningKeyFilterMapImpl(LayeredPropertyMapImpl(filter, PropertyConverter(emptyMap())))
        val filtered = keys.values.filter {
            if(map.category != null && it.category != map.category) {
                false
            } else if(map.schemeCodeName != null && it.schemeCodeName != map.schemeCodeName) {
                false
            } else if(map.alias != null && it.alias != map.alias) {
                false
            } else if(map.masterKeyAlias != null && it.masterKeyAlias != map.masterKeyAlias) {
                false
            } else if(map.createdAfter != null && it.created < map.createdAfter) {
                false
            } else !(map.createdBefore != null && it.created > map.createdBefore)
        }
        return when(orderBy) {
            SigningKeyOrderBy.NONE -> filtered
            SigningKeyOrderBy.ID -> filtered.sortedBy { it.id }
            SigningKeyOrderBy.CREATED -> filtered.sortedBy { it.created }
            SigningKeyOrderBy.CATEGORY -> filtered.sortedBy { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME -> filtered.sortedBy { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS -> filtered.sortedBy { it.alias }
            SigningKeyOrderBy.MASTER_KEY_ALIAS -> filtered.sortedBy { it.masterKeyAlias }
            SigningKeyOrderBy.EXTERNAL_ID -> filtered.sortedBy { it.externalId }
            SigningKeyOrderBy.CREATED_DESC -> filtered.sortedByDescending { it.created }
            SigningKeyOrderBy.CATEGORY_DESC -> filtered.sortedByDescending { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME_DESC -> filtered.sortedByDescending { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS_DESC -> filtered.sortedByDescending { it.alias }
            SigningKeyOrderBy.MASTER_KEY_ALIAS_DESC -> filtered.sortedByDescending { it.masterKeyAlias }
            SigningKeyOrderBy.EXTERNAL_ID_DESC -> filtered.sortedByDescending { it.externalId }
            SigningKeyOrderBy.ID_DESC -> filtered.sortedByDescending { it.id }
        }.drop(skip).take(take)
    }

    override fun lookup(ids: List<String>): Collection<SigningCachedKey> {
        val result = mutableListOf<SigningCachedKey>()
        ids.forEach {
            val found = keys[it]
            if(found != null) {
                result.add(found)
            }
        }
        return result
    }

    override fun close() = Unit
}
