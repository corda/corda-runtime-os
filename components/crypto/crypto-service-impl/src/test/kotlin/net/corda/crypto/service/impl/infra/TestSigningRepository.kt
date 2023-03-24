package net.corda.crypto.service.impl.infra

import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.fullPublicKeyIdFromBytes
import net.corda.crypto.core.publicKeyHashFromBytes
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.persistence.SigningKeyInfo
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
import net.corda.crypto.softhsm.SigningRepository
import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestSigningRepository: SigningRepository {
    private val lock = ReentrantLock()
    private val keys = mutableMapOf<ShortHash, SigningKeyInfo>()

    override fun savePublicKey(context: SigningPublicKeySaveContext): SigningKeyInfo = with(lock) {
        val encodedKey = context.key.publicKey.encoded
        val record = SigningKeyInfo(
            id = publicKeyIdFromBytes(encodedKey),
            fullId = publicKeyHashFromBytes(encodedKey),
            tenantId = "test",
            category = context.category,
            alias = context.alias,
            hsmAlias = context.key.hsmAlias,
            publicKey = encodedKey,
            keyMaterial = null,
            schemeCodeName = context.keyScheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            timestamp = Instant.now(),
            hsmId = context.hsmId,
            status = SigningKeyStatus.NORMAL
        ).also {
            if (keys.putIfAbsent(it.id, it) != null) {
                throw IllegalArgumentException("The key ${it.id} already exists.")
            }
        
    }

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo = with(lock) {
        val encodedKey = context.key.publicKey.encoded
        return SigningKeyInfo(
            id = publicKeyIdFromBytes(encodedKey),
            fullId = publicKeyHashFromBytes(encodedKey),
            tenantId = "test",
            category = context.category,
            alias = context.alias,
            hsmAlias = null,
            publicKey = encodedKey,
            keyMaterial = context.key.keyMaterial,
            schemeCodeName = context.keyScheme.codeName,
            masterKeyAlias = context.masterKeyAlias,
            externalId = context.externalId,
            encodingVersion = context.key.encodingVersion,
            timestamp = Instant.now(),
            hsmId = context.hsmId,
            status = SigningKeyStatus.NORMAL
        ).also {
            if (keys.putIfAbsent(it.id, record) != null) {
            throw IllegalArgumentException("The key ${it.id} already exists.")
        }
    }

    override fun findKey(alias: String): SigningKeyInfo? = lock.withLock {
        keys.values.firstOrNull { it.alias == alias }
    }

    override fun findKey(publicKey: PublicKey): SigningKeyInfo? = lock.withLock {
        keys[fullPublicKeyIdFromBytes(publicKey)]
    }

    @Suppress("ComplexMethod")
    override fun lookup(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> = lock.withLock {
        val map = SigningKeyFilterMapImpl(LayeredPropertyMapImpl(filter, PropertyConverter(emptyMap())))
        val filtered = keys.values.filter {
            if (it.tenantId != tenantId) {
                false
            } else if (map.category != null && it.category != map.category) {
                false
            } else if (map.schemeCodeName != null && it.schemeCodeName != map.schemeCodeName) {
                false
            } else if (map.alias != null && it.alias != map.alias) {
                false
            } else if (map.masterKeyAlias != null && it.masterKeyAlias != map.masterKeyAlias) {
                false
            } else if (map.createdAfter != null && it.timestamp < map.createdAfter) {
                false
            } else !(map.createdBefore != null && it.timestamp > map.createdBefore)
        }
        return when(orderBy) {
            SigningKeyOrderBy.NONE -> filtered
            SigningKeyOrderBy.ID -> filtered.sortedBy { it.id.value }
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
            SigningKeyOrderBy.ID_DESC -> filtered.sortedByDescending { it.id.value }
        }.drop(skip).take(take)
    }

    override fun lookupByIds(tenantId: String, keyIds: List<ShortHash>): Collection<SigningKeyInfo> {
        val result = mutableListOf<SigningKeyInfo>()
        keyIds.forEach {
            val found = keys[Pair(tenantId, it)]
            if (found != null) {
                result.add(found)
            }
        }
        return result
    }

    override fun lookupByFullIds(tenantId: String, fullKeyIds: List<SecureHash>): Collection<SigningKeyInfo> {
        val keyIds = fullKeyIds.map {
            ShortHash.of(it)
        }
        return lookupByIds(tenantId, keyIds)
    }
}

fun fullKeyIdFromBytes(publicKey: ByteArray): SecureHash =
    SecureHashImpl(DigestAlgorithmName.SHA2_256.name, publicKey.sha256Bytes())

fun keyIdFromBytes(publicKey: ByteArray): ShortHash =
    ShortHash.of(fullKeyIdFromBytes(publicKey))

fun keyIdFromKey(publicKey: PublicKey): ShortHash =
    keyIdFromBytes(publicKey.encoded)