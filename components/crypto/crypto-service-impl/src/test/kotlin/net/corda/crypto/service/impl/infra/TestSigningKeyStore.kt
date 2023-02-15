package net.corda.crypto.service.impl.infra

import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.SigningKeyStatus
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
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.publicKeyId
import net.corda.virtualnode.ShortHash
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestSigningKeyStore(
    coordinatorFactory: LifecycleCoordinatorFactory
) : SigningKeyStore {
    val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<SigningKeyStore>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    private val lock = ReentrantLock()
    private val keys = mutableMapOf<Pair<String, String>, SigningCachedKey>()

    override fun save(tenantId: String, context: SigningKeySaveContext) = lock.withLock {
        val now = Instant.now()
        val record = when (context) {
            is SigningPublicKeySaveContext -> {
                val encodedKey = context.key.publicKey.encoded
                SigningCachedKey(
                    id = publicKeyIdFromBytes(encodedKey),
                    fullId = fullPublicKeyIdFromBytes(encodedKey),
                    tenantId = tenantId,
                    category = context.category,
                    alias = context.alias,
                    hsmAlias = context.key.hsmAlias,
                    publicKey = encodedKey,
                    keyMaterial = null,
                    schemeCodeName = context.keyScheme.codeName,
                    masterKeyAlias = null,
                    externalId = null,
                    encodingVersion = null,
                    timestamp = now,
                    hsmId = context.hsmId,
                    status = SigningKeyStatus.NORMAL
                )
            }
            is SigningWrappedKeySaveContext -> {
                val encodedKey = context.key.publicKey.encoded
                SigningCachedKey(
                    id = publicKeyIdFromBytes(encodedKey),
                    fullId = fullPublicKeyIdFromBytes(encodedKey),
                    tenantId = tenantId,
                    category = context.category,
                    alias = context.alias,
                    hsmAlias = null,
                    publicKey = encodedKey,
                    keyMaterial = context.key.keyMaterial,
                    schemeCodeName = context.keyScheme.codeName,
                    masterKeyAlias = context.masterKeyAlias,
                    externalId = context.externalId,
                    encodingVersion = context.key.encodingVersion,
                    timestamp = now,
                    hsmId = context.hsmId,
                    status = SigningKeyStatus.NORMAL
                )
            }
            else -> throw  IllegalArgumentException("Unknown type ${context::class.java.name}")
        }
        if(keys.putIfAbsent(Pair(tenantId, record.id), record) != null) {
            throw IllegalArgumentException("The key ${record.id} already exists.")
        }
    }

    override fun find(tenantId: String, alias: String): SigningCachedKey? = lock.withLock {
        keys.values.firstOrNull { it.tenantId == tenantId && it.alias == alias }
    }

    override fun find(tenantId: String, publicKey: PublicKey): SigningCachedKey? = lock.withLock {
        keys[Pair(tenantId, publicKey.publicKeyId())]
    }

    @Suppress("ComplexMethod")
    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>
    ): Collection<SigningCachedKey> = lock.withLock {
        val map = SigningKeyFilterMapImpl(LayeredPropertyMapImpl(filter, PropertyConverter(emptyMap())))
        val filtered = keys.values.filter {
            if(it.tenantId != tenantId) {
                false
            } else if(map.category != null && it.category != map.category) {
                false
            } else if(map.schemeCodeName != null && it.schemeCodeName != map.schemeCodeName) {
                false
            } else if(map.alias != null && it.alias != map.alias) {
                false
            } else if(map.masterKeyAlias != null && it.masterKeyAlias != map.masterKeyAlias) {
                false
            } else if(map.createdAfter != null && it.timestamp < map.createdAfter) {
                false
            } else !(map.createdBefore != null && it.timestamp > map.createdBefore)
        }
        return when(orderBy) {
            SigningKeyOrderBy.NONE -> filtered
            SigningKeyOrderBy.ID -> filtered.sortedBy { it.id }
            SigningKeyOrderBy.TIMESTAMP -> filtered.sortedBy { it.timestamp }
            SigningKeyOrderBy.CATEGORY -> filtered.sortedBy { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME -> filtered.sortedBy { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS -> filtered.sortedBy { it.alias }
            SigningKeyOrderBy.MASTER_KEY_ALIAS -> filtered.sortedBy { it.masterKeyAlias }
            SigningKeyOrderBy.EXTERNAL_ID -> filtered.sortedBy { it.externalId }
            SigningKeyOrderBy.TIMESTAMP_DESC -> filtered.sortedByDescending { it.timestamp }
            SigningKeyOrderBy.CATEGORY_DESC -> filtered.sortedByDescending { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME_DESC -> filtered.sortedByDescending { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS_DESC -> filtered.sortedByDescending { it.alias }
            SigningKeyOrderBy.MASTER_KEY_ALIAS_DESC -> filtered.sortedByDescending { it.masterKeyAlias }
            SigningKeyOrderBy.EXTERNAL_ID_DESC -> filtered.sortedByDescending { it.externalId }
            SigningKeyOrderBy.ID_DESC -> filtered.sortedByDescending { it.id }
        }.drop(skip).take(take)
    }

    override fun lookupByIds(tenantId: String, keyIds: List<ShortHash>): Collection<SigningCachedKey> {
        val result = mutableListOf<SigningCachedKey>()
        keyIds.forEach {
            val found = keys[Pair(tenantId, it.value)]
            if(found != null) {
                result.add(found)
            }
        }
        return result
    }

    override fun lookupByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): Collection<SigningCachedKey> {
        val keyIds = fullKeyIds.map {
            ShortHash.of(it)
        }
        return lookupByIds(tenantId, keyIds)
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

}
