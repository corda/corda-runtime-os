package net.corda.membership.lib.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.SignatureSpecImpl
import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.membership.lib.exceptions.FailedGroupParametersDeserialization
import net.corda.membership.lib.exceptions.FailedGroupParametersSerialization
import net.corda.membership.lib.toMap
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import net.corda.data.membership.SignedGroupParameters as AvroGroupParameters

@Component(service = [GroupParametersFactory::class])
class GroupParametersFactoryImpl @Activate constructor(
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : GroupParametersFactory {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val avroSerializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
        logger.error("Failed to serialise group parameters to KeyValuePairList.")
    }

    private val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
        logger.error("Failed to deserialise group parameters to KeyValuePairList.")
    }, KeyValuePairList::class.java)

    override fun create(parameters: AvroGroupParameters): InternalGroupParameters = parameters.toCorda()

    override fun create(
        bytes: ByteArray,
        signature: DigitalSignatureWithKey,
        signatureSpec: SignatureSpec
    ): SignedGroupParameters {
        return SignedGroupParametersImpl(
            bytes,
            signature,
            signatureSpec,
            ::deserializeLayeredPropertyMap
        )
    }

    override fun create(parameters: KeyValuePairList): UnsignedGroupParameters = wrapWithNullErrorHandling({
        FailedGroupParametersSerialization("Failed to serialize the GroupParameters to KeyValuePairList", it)
    }) {
        avroSerializer.serialize(parameters)?.toUnsignedGroupParameters()
    }

    private fun ByteArray.toUnsignedGroupParameters(): UnsignedGroupParameters {
        return UnsignedGroupParametersImpl(
            this,
            ::deserializeLayeredPropertyMap
        )
    }

    private fun AvroGroupParameters.toCorda() = mgmSignature?.let { signature ->
        mgmSignatureSpec?.let { spec ->
            SignedGroupParametersImpl(
                groupParameters.array(),
                signature.toCorda(),
                spec.toCorda(),
                ::deserializeLayeredPropertyMap
            )
        }
    } ?: UnsignedGroupParametersImpl(
        groupParameters.array(),
        ::deserializeLayeredPropertyMap
    )

    private fun CryptoSignatureWithKey.toCorda() = DigitalSignatureWithKey(
        keyEncodingService.decodePublicKey(publicKey.array()),
        bytes.array()
    )

    private fun CryptoSignatureSpec.toCorda() = SignatureSpecImpl(signatureName)

    private fun deserializeLayeredPropertyMap(params: ByteArray): LayeredPropertyMap = avroDeserializer
        .deserialize(params)
        ?.let { layeredPropertyMapFactory.createMap(it.toMap()) }
        ?: throw FailedGroupParametersDeserialization
}
