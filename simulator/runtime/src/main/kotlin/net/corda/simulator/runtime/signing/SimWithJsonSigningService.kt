package net.corda.simulator.runtime.signing

import net.corda.crypto.core.DigitalSignatureWithKeyId
import net.corda.crypto.core.fullIdHash
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import org.slf4j.LoggerFactory
import java.security.PublicKey

/**
 * Simulates digital signing.
 *
 * @param keyStore The member's [SimKeyStore] containing the identifying parameters with which the key was created.
 */
class SimWithJsonSigningService(private val keyStore: SimKeyStore) : SigningService {

    private val jsonMarshallingService = SimpleJsonMarshallingService()

    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    /**
     * Wraps the original data with JSON containing the encoded key, identifying parameters and the signature spec.
     *
     * @param bytes The data to "sign".
     * @param publicKey The public key to include in the wrapper.
     * @param signatureSpec The signature spec to include in the wrapper.
     *
     * @return A digital signature object containing a JSON string wrapping the "signed" data, with the parameters
     * with which the data was "signed".
     */
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKeyId {
        return sign(bytes, publicKey, signatureSpec, emptyMap())
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec, context: Map<String, String>): DigitalSignature.WithKeyId {
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
        return DigitalSignatureWithKeyId(publicKey.fullIdHash(), opaqueBytes)
    }

    /**
     * Filters member keys from a provided list of public keys
     * @param keys A set of public keys
     *
     * @return A map containing keys of the member who calls the function
     */
    override fun findMySigningKeys(keys: Set<PublicKey>): Map<PublicKey, PublicKey?> {
        val keyMap = HashMap<PublicKey, PublicKey>()
        keys.filter {
            keyStore.getParameters(it) != null
        }.forEach{
            keyMap[it] = it
        }
        return keyMap
    }
}
