package net.corda.ledger.common.test

import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.ledger.common.testkit.keyPairExample
import net.corda.ledger.common.testkit.publicKeyExample
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.SignatureSpec
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

fun mockSigningService() = mock<SigningService>().also {
    whenever(it.findMySigningKeys(any())).thenReturn(mapOf(publicKeyExample to publicKeyExample))
    whenever(
        it.sign(any(), any(), any())
    ).thenAnswer {
        val signableData = it.arguments.first() as ByteArray
        DigitalSignatureWithKeyId(
            (it.arguments[1] as PublicKey).fullIdHash(),
            signData(signableData, keyPairExample.private, it.arguments[2] as SignatureSpec)
            // The signature won't be consistent with the public key if it is not the publicKeyExample.
            // We can access only its private key easily.
        )
    }
}
private fun signData(data: ByteArray, privateKey: PrivateKey, signatureSpec: SignatureSpec): ByteArray {
    val signature = Signature.getInstance(signatureSpec.signatureName)
    signature.initSign(privateKey)
    signature.update(data)
    return signature.sign()
}