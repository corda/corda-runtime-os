package net.corda.simulator.runtime.signing

import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.exceptions.CryptoSignatureException
import java.security.PublicKey

/**
 * Simulates digital signature validation.
 */
class SimWithJsonSignatureVerificationService : DigitalSignatureVerificationService {

    private val jsonMarshallingService = SimpleJsonMarshallingService()

    /**
     * Parses the wrapper which was created by the simulated [net.corda.v5.application.crypto.SigningService]
     * and checks that the clear data, encoded key and signature spec in that wrapper are a match for the parameters
     * passed in here.
     *
     * @param publicKey The public key that should have been encoded and embedded in the signatureData.
     * @param signatureSpec The signature spec that should have been embedded in the signatureData.
     * @param signatureData A JSON wrapper containing the above parameters around the clear data.
     * @param clearData The data to match against the "signed" data.
     *
     * @throws CryptoSignatureException if the parameters or the clear data in the wrapper are not a match.
     */
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
            throw CryptoSignatureException(
                "Signature spec did not match; " +
                        "expected ${signatureSpec.signatureName} but was ${wrapper.signatureSpecName}"
            )
        }
        if (!clearData.contentEquals(wrapper.clearData)) {
            throw CryptoSignatureException("Clear data did not match")
        }
    }

}
