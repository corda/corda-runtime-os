package net.corda.crypto.service.impl.infra

import java.security.PublicKey
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.publicKeyHashFromBytes
import net.corda.crypto.core.publicKeyShortHashFromBytes
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
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
import net.corda.v5.crypto.SecureHash
import kotlin.concurrent.withLock

class TestSigningRepository: SigningRepository {
    private val lock = ReentrantLock()
    private val keys = mutableMapOf<ShortHash, SigningKeyInfo>()

    override fun savePublicKey(context: SigningPublicKeySaveContext): SigningKeyInfo = lock.withLock {
        val encodedKey = context.key.publicKey.encoded
        return SigningKeyInfo(
            id = publicKeyShortHashFromBytes(encodedKey),
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
    }

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo = lock.withLock {
        val encodedKey = context.key.publicKey.encoded
        return SigningKeyInfo(
            id = publicKeyShortHashFromBytes(encodedKey),
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
            if (keys.putIfAbsent(it.id, it) != null) {
                throw IllegalArgumentException("The key ${it.id} already exists.")
            }
        }
    }

    override fun findKey(alias: String): SigningKeyInfo? = lock.withLock {
        keys.values.firstOrNull { it.alias == alias }
    }

    override fun findKey(publicKey: PublicKey): SigningKeyInfo? = lock.withLock {
        keys[ShortHash.of(publicKeyHashFromBytes(publicKey.encoded))]
    }

    @Suppress("ComplexMethod")
    override fun query(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> = lock.withLock {
        val map = SigningKeyFilterMapImpl(LayeredPropertyMapImpl(filter, PropertyConverter(emptyMap())))
        val filtered = keys.values.filter {
            if (map.category != null && it.category != map.category) {
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

    override fun lookupByPublicKeyShortHashes(keyIds: Set<ShortHash>): Collection<SigningKeyInfo> {
        return keys.filter { it.key in keyIds }.values
    }

    override fun lookupByPublicKeyHashes(fullKeyIds: Set<SecureHash>): Collection<SigningKeyInfo> {
        return lookupByPublicKeyShortHashes(fullKeyIds.map { ShortHash.of(it) }.toSet())
    }

    override fun close() { keys.clear() }
}
