package net.corda.membership.impl.registration.dynamic.mgm.handler.helpers

import net.corda.crypto.client.CryptoOpsClient
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.schemes.EDDSA_ED25519_TEMPLATE
import net.corda.v5.cipher.suite.schemes.GOST3410_GOST3411_TEMPLATE
import net.corda.v5.crypto.ECDSA_SECP256K1_CODE_NAME
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SM2_CODE_NAME
import net.corda.v5.crypto.SPHINCS256_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.publicKeyId
import java.security.PublicKey

internal class Signer(
    private val tenantId: String,
    private val publicKey: PublicKey,
    private val cryptoOpsClient: CryptoOpsClient,
) {
    private companion object {
        val defaultCodeNameToSpec = mapOf(
            ECDSA_SECP256K1_CODE_NAME to SignatureSpec("SHA512withECDSA"),
            ECDSA_SECP256R1_CODE_NAME to SignatureSpec("SHA512withECDSA"),
            EDDSA_ED25519_TEMPLATE to SignatureSpec.EDDSA_ED25519,
            GOST3410_GOST3411_TEMPLATE to SignatureSpec.GOST3410_GOST3411,
            RSA_CODE_NAME to SignatureSpec.RSA_SHA512,
            SM2_CODE_NAME to SignatureSpec.SM2_SM3,
            SPHINCS256_CODE_NAME to SignatureSpec.SPHINCS256_SHA512,
        )
    }

    private val spec by lazy {
        val keyInfo = cryptoOpsClient.lookup(
            tenantId,
            listOf(publicKey.publicKeyId()),
        ).firstOrNull() ?: throw CordaRuntimeException("Public key is not owned by $tenantId")
        defaultCodeNameToSpec[keyInfo.schemeCodeName]
            ?: throw CordaRuntimeException("Can not find spec for ${keyInfo.schemeCodeName}")
    }

    fun sign(data: ByteArray) =
        cryptoOpsClient.sign(
            tenantId = tenantId,
            publicKey = publicKey,
            data = data,
            signatureSpec = spec,
        )
}
