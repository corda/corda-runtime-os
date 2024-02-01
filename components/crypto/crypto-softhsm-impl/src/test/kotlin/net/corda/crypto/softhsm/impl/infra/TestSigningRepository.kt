package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.SigningKeyStatus
import net.corda.crypto.core.publicKeyHashFromBytes
import net.corda.crypto.core.publicKeyShortHashFromBytes
import net.corda.crypto.persistence.SigningKeyMaterialInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.SecureHash
import java.lang.IllegalStateException
import java.security.PublicKey
import java.time.Instant
import java.util.UUID

class TestSigningRepository(val tenantId: String = "test") : SigningRepository {
    private val keys = mutableMapOf<ShortHash, SigningKeyInfo>()

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo {
        val encodedKey = context.key.publicKey.encoded
        return SigningKeyInfo(
            id = publicKeyShortHashFromBytes(encodedKey),
            fullId = publicKeyHashFromBytes(encodedKey),
            tenantId = tenantId,
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

    override fun findKey(alias: String): SigningKeyInfo? = null
    override fun findKey(publicKey: PublicKey): SigningKeyInfo? = null

    override fun query(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> = throw NotImplementedError()

    override fun lookupByPublicKeyShortHashes(keyIds: Set<ShortHash>): Collection<SigningKeyInfo> =
        throw NotImplementedError()

    override fun lookupByPublicKeyHashes(fullKeyIds: Set<SecureHash>): Collection<SigningKeyInfo> =
        throw NotImplementedError()

    override fun close() {

    }

    override fun getKeyMaterials(wrappingKeyId: UUID): Collection<SigningKeyMaterialInfo> {
        throw IllegalStateException("Unexpected call to getKeyMaterials")
    }

    override fun saveSigningKeyMaterial(signingKeyMaterialInfo: SigningKeyMaterialInfo, wrappingKeyId: UUID) {
        throw IllegalStateException("Unexpected call to saveSigningKeyMaterial")
    }
}