package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryKeys
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMutableMap
import net.corda.membership.lib.toWire
import net.corda.virtualnode.toCorda

internal class AddNotaryToGroupParametersHandler(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<AddNotaryToGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private companion object {
        const val NOTARY_SERVICE_NAME_KEY = "corda.notary.service.%s.name"
        const val NOTARY_SERVICE_PLUGIN_KEY = "corda.notary.service.%s.plugin"
        const val NOTARY_KEYS_KEY = "corda.notary.service.%s.keys.%s"
        const val NOTARY_SERVICE_KEYS_PREFIX = "corda.notary.service.%s.keys"
        const val NOTARY_PLUGIN_KEY = "corda.notary.service.plugin"
        const val EPOCH_KEY = "corda.epoch"
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

            val parametersMap = deserializeProperties(previous.parameters).toMutableMap()
            val previousEpoch = parametersMap[EPOCH_KEY].toString().toInt()

            val notary = memberInfoFactory.create(request.notary)
            val notaryServiceName = notary.memberProvidedContext[NOTARY_SERVICE_PARTY_NAME]
                ?: throw MembershipPersistenceException("Cannot add notary to group parameters - missing notary service name.")
            val notaryServicePlugin = notary.memberProvidedContext[NOTARY_PLUGIN_KEY]
            val notaryServiceNumber = parametersMap.entries.firstOrNull { it.value == notaryServiceName }?.run {
                key.split(".")[3].toInt()
            }
            if (notaryServiceNumber != null) {
                // Add notary to existing notary service, or update notary with rotated keys
                notaryServicePlugin?.let {
                    require(parametersMap[String.format(NOTARY_SERVICE_PLUGIN_KEY, notaryServiceNumber)].toString() == it) {
                        throw MembershipPersistenceException("Cannot add notary '${notary.name}' to notary service " +
                                "'$notaryServiceName' - plugin types do not match.")
                    }
                }
                val notaryKeys = parametersMap.entries
                    .filter { it.key.startsWith(String.format(NOTARY_SERVICE_KEYS_PREFIX, notaryServiceNumber)) }
                    .map { it.value }
                var newIndex = notaryKeys.size
                notary.notaryKeys
                    .map { keyEncodingService.encodeAsString(it) }
                    .filterNot { notaryKeys.contains(it) }
                    .apply {
                        if (isEmpty()) {
                            logger.warn("Group parameters not updated. Notary '${notary.name}' and its notary keys are" +
                                    " already listed under notary service '$notaryServiceName'.")
                            return@transaction previousEpoch
                        }
                    }
                    .forEach {
                        parametersMap[String.format(NOTARY_KEYS_KEY, notaryServiceNumber, newIndex++)] = it
                    }
            } else {
                // Add new notary service
                requireNotNull(notaryServicePlugin) {
                    throw MembershipPersistenceException("Cannot add notary to group parameters - notary plugin must be" +
                            " specified to create new notary service '$notaryServiceName'.")
                }
                val newNotaryServiceNumber = parametersMap
                    .filter { NOTARY_SERVICE_NAME_KEY.format("[0-9]+").toRegex().matches(it.key) }.size
                var newIndex = 0
                parametersMap[String.format(NOTARY_SERVICE_NAME_KEY, newNotaryServiceNumber)] = notaryServiceName
                parametersMap[String.format(NOTARY_SERVICE_PLUGIN_KEY, newNotaryServiceNumber)] = notaryServicePlugin
                notary.notaryKeys
                    .map { persistenceHandlerServices.keyEncodingService.encodeAsString(it) }
                    .forEach {
                        parametersMap[String.format(NOTARY_KEYS_KEY, newNotaryServiceNumber, newIndex++)] = it
                     }
            }
            // Update epoch
            val newEpoch = with(parametersMap) {
                this[EPOCH_KEY] = (previousEpoch + 1).toString()
                previousEpoch + 1
            }

            val entity = GroupParametersEntity(
                epoch = newEpoch,
                parameters = serializeProperties(parametersMap.toWire()),
            )
            em.persist(entity)

            entity.epoch
        }

        return PersistGroupParametersResponse(epoch)
    }
}
