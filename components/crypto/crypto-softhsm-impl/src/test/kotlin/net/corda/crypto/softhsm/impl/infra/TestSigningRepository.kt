package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.SecureHash
import java.security.PublicKey

class TestSigningRepository : SigningRepository {
    override fun savePublicKey(context: SigningPublicKeySaveContext): SigningKeyInfo = throw NotImplementedError()

    override fun savePrivateKey(context: SigningWrappedKeySaveContext): SigningKeyInfo = throw NotImplementedError()
    override fun findKey(alias: String): SigningKeyInfo? = throw NotImplementedError()
    override fun findKey(publicKey: PublicKey): SigningKeyInfo? =  throw NotImplementedError()

    override fun query(
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningKeyInfo> = throw NotImplementedError()

    override fun lookupByPublicKeyShortHashes(keyIds: Set<ShortHash>): Collection<SigningKeyInfo> = throw NotImplementedError()

    override fun lookupByPublicKeyHashes(fullKeyIds: Set<SecureHash>): Collection<SigningKeyInfo> = throw NotImplementedError()

    override fun close() {

    }
}