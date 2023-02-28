package net.corda.membership.lib.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.membership.lib.FailedGroupParametersDeserialization
import net.corda.membership.lib.GroupParametersFactory
import net.corda.membership.lib.toMap
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters
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

    private val avroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer({
        logger.error("Failed to deserialise group parameters to KeyValuePairList.")
    }, KeyValuePairList::class.java)

    override fun create(parameters: AvroGroupParameters): GroupParameters {
        val deserialisedParams = avroDeserializer.deserialize(parameters.groupParameters.array())
            ?: throw FailedGroupParametersDeserialization
        val groupParams = create(deserialisedParams)
        return if (parameters.mgmSignature == null) {
            groupParams
        } else {
            SignedGroupParametersImpl(
                groupParams,
                parameters.groupParameters.array(),
                parameters.mgmSignature.let {
                    DigitalSignature.WithKey(
                        by = keyEncodingService.decodePublicKey(it.publicKey.array()),
                        bytes = it.bytes.array(),
                        context = it.context.toMap()
                    )
                }
            )
        }
    }

    override fun create(parameters: KeyValuePairList): GroupParameters =
        GroupParametersImpl(layeredPropertyMapFactory.createMap(parameters.toMap()))
}
