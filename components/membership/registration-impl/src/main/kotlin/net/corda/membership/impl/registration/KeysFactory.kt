package net.corda.membership.impl.registration

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.p2p.helpers.KeySpecExtractor
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.calculateHash
import java.security.PublicKey

internal class KeysFactory(
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val scheme: String,
    private val tenantId: String,
) {
    private val keySpecExtractor by lazy {
        KeySpecExtractor(tenantId, cryptoOpsClient)
    }
    fun getOrGenerateKeyPair(category: String): Key {
        val alias = "$tenantId-$category"
        val key = cryptoOpsClient.lookup(
            tenantId = tenantId,
            skip = 0,
            take = 10,
            orderBy = CryptoKeyOrderBy.NONE,
            filter = mapOf(
                ALIAS_FILTER to alias,
                CATEGORY_FILTER to category,
            )
        ).firstOrNull()?.let {
            keyEncodingService.decodePublicKey(it.publicKey.array())
        } ?: cryptoOpsClient.generateKeyPair(
            tenantId = tenantId,
            category = category,
            alias = alias,
            scheme = scheme
        )
        return Key(key)
    }

    inner class Key(
        publicKey: PublicKey,
    ) {
        val pem by lazy {
            keyEncodingService.encodeAsString(publicKey)
        }
        val hash by lazy {
            publicKey.calculateHash()
        }
        val spec by lazy {
            keySpecExtractor.getSpec(publicKey)
        }
    }
}
