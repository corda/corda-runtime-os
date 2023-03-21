package net.corda.crypto.softhsm.impl.infra

import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.HSMUsage
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.v5.crypto.SecureHash

class TestCryptoRepository(
    val keys: ConcurrentHashMap<String, WrappingKeyInfo> = ConcurrentHashMap(),
) : CryptoRepository {
    val findCounter = mutableMapOf<String, Int>()

    override fun saveWrappingKey(alias: String, key: WrappingKeyInfo) {
        keys[alias] = key
    }

    override fun findWrappingKey(alias: String): WrappingKeyInfo? {
        findCounter[alias] = findCounter[alias]?.plus(1) ?: 1
        return keys[alias]
    }

    override fun saveSigningKey(tenantId: String, context: SigningKeySaveContext) {
        TODO("Not yet implemented")
    }

    override fun findSigningKey(tenantId: String, alias: String): SigningCachedKey? {
        TODO("Not yet implemented")
    }

    override fun findSigningKey(tenantId: String, publicKey: PublicKey): SigningCachedKey? {
        TODO("Not yet implemented")
    }

    override fun lookupSigningKey(
        tenantId: String,
        skip: Int,
        take: Int,
        orderBy: SigningKeyOrderBy,
        filter: Map<String, String>,
    ): Collection<SigningCachedKey> {
        TODO("Not yet implemented")
    }

    override fun lookupSigningKeysByIds(tenantId: String, keyIds: Set<ShortHash>): Collection<SigningCachedKey> {
        TODO("Not yet implemented")
    }

    override fun lookupSigningKeysByFullIds(
        tenantId: String,
        fullKeyIds: Set<SecureHash>,
    ): Collection<SigningCachedKey> {
        TODO("Not yet implemented")
    }

    override fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? {
        TODO("Not yet implemented")
    }

    override fun getHSMUsage(): List<HSMUsage> {
        TODO("Not yet implemented")
    }

    override fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy,
    ): HSMAssociationInfo {
        TODO("Not yet implemented")
    }

    override fun close() {
    }
}