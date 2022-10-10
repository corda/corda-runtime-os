package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_NAME
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class AddNotaryToGroupParametersHandler(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<AddNotaryToGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private companion object {
        const val NOTARY_SERVICE_NAME_KEY = "corda.notary.service.%s.name"
        const val NOTARY_SERVICE_PLUGIN_KEY = "corda.notary.service.%s.plugin"
        const val NOTARY_KEYS_KEY = "corda.notary.service.%s.keys.%s"
        const val NOTARY_SERVICE_KEYS_PREFIX = "corda.notary.service.%.keys"
    }

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    private fun deserializeProperties(data: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(data) ?: throw MembershipPersistenceException(
            "Failed to deserialize key value pair list."
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: AddNotaryToGroupParameters
    ): PersistGroupParametersResponse {
        val epoch = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val previous = em.createQuery(
                "SELECT c FROM ${GroupParametersEntity::class.simpleName} c " +
                        "ORDER BY c.epoch DESC",
                GroupParametersEntity::class.java
            ).resultList.firstOrNull()
                ?: throw MembershipPersistenceException("Cannot add notary to group parameters, no group parameters found.")

            val parameters = deserializeProperties(previous.parameters)

            val notary = persistenceHandlerServices.memberInfoFactory.create(request.notary)
            val notaryServiceName = notary.memberProvidedContext[NOTARY_SERVICE_PARTY_NAME]
                ?: throw MembershipPersistenceException("Cannot add notary to group parameters - missing notary service name.")
            val notaryServicePlugin = notary.memberProvidedContext["corda.notary.service.plugin"]
                ?: throw MembershipPersistenceException("Cannot add notary to group parameters - missing notary service plugin.")
            val notaryServiceNumber = parameters.items.firstOrNull { it.value == notaryServiceName }?.run {
                key.split(".")[3].toInt()
            }
            if (notaryServiceNumber != null &&
                parameters[String.format(NOTARY_SERVICE_PLUGIN_KEY, notaryServiceNumber)] == notaryServicePlugin) {
                // Add notary to existing notary service, or update notary with rotated keys
                val notaryKeys = parameters.items
                    .filter { it.key.startsWith(String.format(NOTARY_SERVICE_KEYS_PREFIX, notaryServiceNumber)) }
                    .map { it.value }
                var newIndex = notaryKeys.size
                notary.ledgerKeys
                    .map { persistenceHandlerServices.keyEncodingService.encodeAsString(it) }
                    .filterNot { notaryKeys.contains(it) }
                    .forEach { parameters.items.add(KeyValuePair(
                        String.format(NOTARY_KEYS_KEY, notaryServiceNumber, newIndex++),
                        it
                    )) }
            } else {
                // Add new notary service
                val newNotaryServiceNumber = parameters.items
                    .filter { NOTARY_SERVICE_NAME_KEY.format("[0-9]+").toRegex().matches(it.key) }.size
                var newIndex = 0
                parameters.items.add(KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, newNotaryServiceNumber), notaryServiceName))
                parameters.items.add(KeyValuePair(String.format(NOTARY_SERVICE_PLUGIN_KEY, newNotaryServiceNumber), notaryServicePlugin))
                notary.ledgerKeys
                    .map { persistenceHandlerServices.keyEncodingService.encodeAsString(it) }
                    .forEach { parameters.items.add(KeyValuePair(
                        String.format(NOTARY_KEYS_KEY, newNotaryServiceNumber, newIndex++),
                        it
                    )) }
            }

            val entity = GroupParametersEntity(
                epoch = previous.epoch?.plus(1),
                lastModified = clock.instant(),
                parameters = serializeProperties(parameters),
            )
            em.persist(entity)

            entity.epoch
        }

        return PersistGroupParametersResponse(epoch)
    }
}
