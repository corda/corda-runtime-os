package net.corda.simulator.runtime.signing

import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

/**
 * Simulates digital signing.
 *
 * @param jsonMarshallingService A JSON marshalling service with which to format the wrapper.
 * @param keyStore The member's [SimKeyStore] containing the identifying parameters with which the key was created.
 */
class SimWithJsonSigningService(private val keyStore: SimKeyStore) : SigningService {

    private val jsonMarshallingService = SimpleJsonMarshallingService()

    companion object {
        val log = contextLogger()
    }

    /**
     * Wraps the clear data with JSON containing the encoded key, identifying parameters and the signature spec.
     *
     * @param bytes The data to "sign".
     * @param publicKey The public key to include in the wrapper.
     * @param signatureSpec The signature spec to incldue in the wrapper.
     *
     * @return A digital signature object containing a JSON string wrapping the "signed" data, with the parameters
     * with which the data was "signed".
     */
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        log.info("Simulating signing of bytes: $bytes")
        val keyParameters = checkNotNull(keyStore.getParameters(publicKey)) {
            "Attempted signing, but key has not been generated on the given node. Bytes being signed were" +
                    System.lineSeparator() +
                    bytes.decodeToString()
        }
        val pemEncodedPublicKey = pemEncode(publicKey)
        val opaqueBytes = jsonMarshallingService.format(
            SimJsonSignedWrapper(
                bytes,
                pemEncodedPublicKey,
                signatureSpec.signatureName,
                keyParameters
            )
        ).toByteArray()
        return DigitalSignature.WithKey(publicKey, opaqueBytes, mapOf())
    }

    override fun findMySigningKeys(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        TODO("Not yet implemented")
    }

}
