package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.SignedGroupParameters
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.exceptions.FailedGroupParametersDeserialization
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.membership.lib.exceptions.FailedGroupParametersSerialization
import net.corda.membership.lib.toMap
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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

    private val avroSerializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList>{
        logger.error("Failed to serialise group parameters to KeyValuePairList.")
    }

    private val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
        logger.error("Failed to deserialise group parameters to KeyValuePairList.")
    }, KeyValuePairList::class.java)

    override fun create(parameters: SignedGroupParameters): GroupParameters = parameters.toCorda()

    override fun create(parameters: KeyValuePairList): GroupParameters =
        avroSerializer.serialize(parameters)?.toUnsignedGroupParameters() ?: throw FailedGroupParametersSerialization


    private fun ByteArray.toUnsignedGroupParameters(): UnsignedGroupParameters {
        return UnsignedGroupParametersImpl(
            this,
            ::deserializeLayeredPropertyMap
        )
    }

    private fun SignedGroupParameters.toCorda(): GroupParameters {
        return if (mgmSignature == null) {
            groupParameters.array().toUnsignedGroupParameters()
        } else {
            SignedGroupParametersImpl(
                groupParameters.array(),
                mgmSignature.let {
                    DigitalSignature.WithKey(
                        by = keyEncodingService.decodePublicKey(it.publicKey.array()),
                        bytes = it.bytes.array(),
                        context = it.context.toMap()
                    )
                },
                ::deserializeLayeredPropertyMap
            )
        }
    }

    private fun deserializeLayeredPropertyMap(params: ByteArray): LayeredPropertyMap = avroDeserializer
        .deserialize(params)
        ?.let { layeredPropertyMapFactory.createMap(it.toMap()) }
        ?: throw FailedGroupParametersDeserialization
}
