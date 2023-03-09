package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.membership.lib.exceptions.FailedGroupParametersDeserialization
import net.corda.membership.lib.exceptions.FailedGroupParametersSerialization
import net.corda.membership.lib.toMap
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.DigitalSignature
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

    override fun create(parameters: KeyValuePairList): UnsignedGroupParameters =
        avroSerializer.serialize(parameters)?.toUnsignedGroupParameters() ?: throw FailedGroupParametersSerialization


    private fun ByteArray.toUnsignedGroupParameters(): UnsignedGroupParameters {
        return UnsignedGroupParametersImpl(
            this,
            ::deserializeLayeredPropertyMap
        )
    }

    private fun AvroGroupParameters.toCorda() = mgmSignature?.let {
        SignedGroupParametersImpl(
            groupParameters.array(),
            it.toCorda(),
            ::deserializeLayeredPropertyMap
        )
    } ?: UnsignedGroupParametersImpl(
        groupParameters.array(),
        ::deserializeLayeredPropertyMap
    )

    private fun CryptoSignatureWithKey.toCorda() = DigitalSignature.WithKey(
        keyEncodingService.decodePublicKey(publicKey.array()),
        bytes.array(),
        context.toMap()
    )

    private fun deserializeLayeredPropertyMap(params: ByteArray): LayeredPropertyMap = avroDeserializer
        .deserialize(params)
        ?.let { layeredPropertyMapFactory.createMap(it.toMap()) }
        ?: throw FailedGroupParametersDeserialization
}
