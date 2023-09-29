package net.corda.interop.identity.write.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.publicKeyId
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.crypto.wire.ops.rpc.queries.CryptoKeyOrderBy
import net.corda.interop.core.InteropIdentity
import net.corda.membership.certificate.client.CertificatesResourceNotFoundException


@Suppress("ForbiddenComment")
class SessionKeyGenerator(
    private val cryptoOpsClient: CryptoOpsClient,
    private val keyEncodingService: KeyEncodingService,
    private val hsmRegistrationClient: HSMRegistrationClient
) {
    companion object {
        private const val CATEGORY = "SESSION_INIT"
        private const val ALIAS = "alias"
        private const val SCHEME = "CORDA.ECDSA.SECP256R1"
    }

    fun getOrCreateSessionKey(
        identityToPublish: InteropIdentity,
        vNodeShortHash: String
    ): String {
        val alias = "INTEROP-SESSION-${identityToPublish.x500Name}"
        val sessionKeyList = cryptoOpsClient.lookup(
            tenantId = vNodeShortHash,
            skip = 0,
            take = 10,
            orderBy = CryptoKeyOrderBy.ID,
            filter = mapOf(ALIAS to alias)
        )
        return if (sessionKeyList.isNotEmpty()) {
            sessionKeyList[0].toPem()
        } else {
            hsmRegistrationClient.assignSoftHSM(vNodeShortHash, CATEGORY)
            val sessionKeyId = cryptoOpsClient.generateKeyPair(
                tenantId = vNodeShortHash,
                category = CATEGORY,
                alias = alias,
                scheme = SCHEME,
            )
            getSessionKey(vNodeShortHash, ShortHash.of(sessionKeyId.publicKeyId()))
        }
    }

    private fun getSessionKey(
        tenantId: String,
        sessionKeyId: ShortHash,
    ): String {
        return cryptoOpsClient.lookupKeysByIds(
            tenantId = tenantId,
            keyIds = listOf(sessionKeyId)
        ).firstOrNull()
            ?.toPem()
            ?: throw CertificatesResourceNotFoundException("Can not find interop session key for $tenantId")
    }

    private fun CryptoSigningKey.toPem(): String {
        return keyEncodingService.encodeAsString(
            keyEncodingService.decodePublicKey(
                this.publicKey.array()
            )
        )
    }
}
