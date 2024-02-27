package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.ledger.persistence.PersistSignedGroupParametersIfDoNotExist
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Clock
import java.util.Objects
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

@Component(service = [ExternalEventFactory::class])
class PersistSignedGroupParametersIfDoNotExistExternalEventFactory constructor(
    private val keyEncodingService: KeyEncodingService,
    clock: Clock = Clock.systemUTC()
) :
    AbstractUtxoLedgerExternalEventFactory<PersistSignedGroupParametersIfDoNotExistParameters>(clock) {
    @Activate
    constructor(
        @Reference(service = KeyEncodingService::class)
        keyEncodingService: KeyEncodingService
    ) : this(keyEncodingService, Clock.systemUTC())

    override fun createRequest(parameters: PersistSignedGroupParametersIfDoNotExistParameters): Any {
        return PersistSignedGroupParametersIfDoNotExist(
            AvroGroupParameters(
                ByteBuffer.wrap(parameters.bytes),
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(keyEncodingService.encodeAsByteArray(parameters.signature.by)),
                    ByteBuffer.wrap(parameters.signature.bytes)
                ),
                CryptoSignatureSpec(parameters.signatureSpec.signatureName, null, null)
            )
        )
    }
}

@CordaSerializable
data class PersistSignedGroupParametersIfDoNotExistParameters(
    val bytes: ByteArray,
    val signature: DigitalSignatureWithKey,
    val signatureSpec: SignatureSpec
) {
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is PersistSignedGroupParametersIfDoNotExistParameters) return false
        if (this === other) return true
        return bytes.contentEquals(other.bytes) &&
            signature == other.signature &&
            signatureSpec == other.signatureSpec
    }

    override fun hashCode(): Int = Objects.hash(bytes, signature, signatureSpec)
}
