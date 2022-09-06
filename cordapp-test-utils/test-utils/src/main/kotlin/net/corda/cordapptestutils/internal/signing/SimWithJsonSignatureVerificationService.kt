package net.corda.cordapptestutils.internal.signing

import net.corda.cordapptestutils.internal.tools.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import java.security.PublicKey

class SimWithJsonSignatureVerificationService : DigitalSignatureVerificationService {

    private val jsonMarshallingService = SimpleJsonMarshallingService()

    override fun verify(
        publicKey: PublicKey,
        signatureSpec: SignatureSpec,
        signatureData: ByteArray,
        clearData: ByteArray
    ) {
        val wrapper = jsonMarshallingService.parse(String(signatureData), SimJsonSignedWrapper::class.java)
        val encodedKey = pemEncode(publicKey)

        if (encodedKey != wrapper.pemEncodedPublicKey) {
            throw CryptoSignatureException("Public key did not match")
        }
        if (signatureSpec.signatureName != wrapper.signatureSpecName) {
            throw CryptoSignatureException("Signature spec did not match; " +
                    "expected ${signatureSpec.signatureName} but was ${wrapper.signatureSpecName}")
        }
        if (!clearData.contentEquals(wrapper.clearData)) {
            throw CryptoSignatureException("Clear data did not match")
        }
    }

}
