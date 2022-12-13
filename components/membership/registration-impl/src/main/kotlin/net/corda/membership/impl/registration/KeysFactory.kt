package net.corda.membership.impl.registration

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.membership.p2p.helpers.KeySpecExtractor
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

    fun getOrGenerateKeyPair(category: String): KeyDetails {
        val alias = "$tenantId-$category"
        val key = try {
            cryptoOpsClient.generateKeyPair(
                tenantId = tenantId,
                category = category,
                alias = alias,
                scheme = scheme
            )
        } catch (e: KeyAlreadyExistsException) {
            cryptoOpsClient.lookup(
                tenantId = tenantId,
                skip = 0,
                take = 1,
                orderBy = CryptoKeyOrderBy.NONE,
                filter = mapOf(
                    ALIAS_FILTER to alias,
                    CATEGORY_FILTER to category,
                )
            ).first().let {
                keyEncodingService.decodePublicKey(it.publicKey.array())
            }
        }
        return Key(key)
    }

    private inner class Key(
        publicKey: PublicKey,
    ) : KeyDetails {
        override val pem by lazy {
            keyEncodingService.encodeAsString(publicKey)
        }
        override val hash by lazy {
            publicKey.calculateHash()
        }
        override val spec by lazy {
            keySpecExtractor.getSpec(publicKey)
        }
    }
}
