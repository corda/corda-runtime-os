package net.corda.crypto.service.impl.infra

import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.SigningKeyStatus
import net.corda.crypto.core.publicKeyHashFromBytes
import net.corda.crypto.core.publicKeyShortHashFromBytes
import net.corda.crypto.persistence.SigningKeyFilterMapImpl
import net.corda.crypto.persistence.SigningKeyMaterialInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
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
import java.lang.IllegalStateException
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TestSigningRepository : SigningRepository {
    private val lock = ReentrantLock()
    private val keys = mutableMapOf<ShortHash, SigningKeyInfo>()

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo = lock.withLock {
        val encodedKey = context.key.publicKey.encoded
        return SigningKeyInfo(
            id = publicKeyShortHashFromBytes(encodedKey),
            fullId = publicKeyHashFromBytes(encodedKey),
            tenantId = "test",
            category = context.category,
            alias = context.alias,
            hsmAlias = null,
            publicKey = context.key.publicKey,
            keyMaterial = context.key.keyMaterial,
            schemeCodeName = context.keyScheme.codeName,
            wrappingKeyAlias = context.wrappingKeyAlias,
            externalId = context.externalId,
            encodingVersion = context.key.encodingVersion,
            timestamp = Instant.now(),
            "SOFT",
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
            } else if (map.masterKeyAlias != null && it.wrappingKeyAlias != map.masterKeyAlias) {
                false
            } else if (map.createdAfter != null && it.timestamp < map.createdAfter) {
                false
            } else !(map.createdBefore != null && it.timestamp > map.createdBefore)
        }
        return when (orderBy) {
            SigningKeyOrderBy.NONE -> filtered
            SigningKeyOrderBy.ID -> filtered.sortedBy { it.id.value }
            SigningKeyOrderBy.TIMESTAMP -> filtered.sortedBy { it.timestamp }
            SigningKeyOrderBy.CATEGORY -> filtered.sortedBy { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME -> filtered.sortedBy { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS -> filtered.sortedBy { it.alias }
            SigningKeyOrderBy.EXTERNAL_ID -> filtered.sortedBy { it.externalId }
            SigningKeyOrderBy.TIMESTAMP_DESC -> filtered.sortedByDescending { it.timestamp }
            SigningKeyOrderBy.CATEGORY_DESC -> filtered.sortedByDescending { it.category }
            SigningKeyOrderBy.SCHEME_CODE_NAME_DESC -> filtered.sortedByDescending { it.schemeCodeName }
            SigningKeyOrderBy.ALIAS_DESC -> filtered.sortedByDescending { it.alias }
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

    override fun close() {
        // We do not clear keys here, since we want to be able to reuse the repository.
    }

    override fun getKeyMaterials(wrappingKeyId: UUID): Collection<SigningKeyMaterialInfo> {
        throw IllegalStateException("Unexpected call to getKeyMaterials")
    }

    override fun saveSigningKeyMaterial(signingKeyMaterialInfo: SigningKeyMaterialInfo, wrappingKeyId: UUID) {
        throw IllegalStateException("Unexpected call to saveSigningKeyMaterial")
    }
}
