package net.corda.crypto.softhsm.impl.infra

import net.corda.crypto.core.ShortHash
import net.corda.crypto.core.SigningKeyInfo
import net.corda.crypto.core.fullIdHash
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey

class TestSigningRepository : SigningRepository {
    override fun savePublicKey(context: SigningPublicKeySaveContext): SigningKeyInfo = throw NotImplementedError()

    override fun savePrivateKey(context: SigningWrappedKeySaveContext) = mock<SigningKeyInfo> {
        on { id } doReturn ShortHash.of(context.key.publicKey.fullIdHash().toHexString())
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