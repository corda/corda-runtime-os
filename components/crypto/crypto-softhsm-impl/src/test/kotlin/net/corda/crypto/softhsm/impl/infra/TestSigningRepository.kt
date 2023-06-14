package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.SigningKeyStatus
import net.corda.crypto.core.publicKeyHashFromBytes
import net.corda.crypto.core.publicKeyShortHashFromBytes
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey
import java.time.Instant

class TestSigningRepository : SigningRepository {
    private val keys = mutableMapOf<ShortHash, SigningKeyInfo>()

    override fun savePublicKey(context: SigningPublicKeySaveContext): SigningKeyInfo = throw NotImplementedError()

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo {
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
}