package net.corda.simulator.runtime.signing

import java.security.PublicKey
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec

class SimWithJsonSigningService(
    private val jsonMarshallingService: JsonMarshallingService,
    private val keyStore: SimKeyStore) : SigningService {

    companion object {
        val log = contextLogger()
    }

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
}
