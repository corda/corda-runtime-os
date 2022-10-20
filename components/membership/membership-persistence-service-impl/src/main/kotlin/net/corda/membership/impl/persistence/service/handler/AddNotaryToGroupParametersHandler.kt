package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda

internal class AddNotaryToGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<AddNotaryToGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private companion object {
        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
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
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(GroupParametersEntity::class.java)
            val root = queryBuilder.from(GroupParametersEntity::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))
            val previous = em.createQuery(query)
                .setMaxResults(1)
                .singleResult
                ?: throw MembershipPersistenceException("Cannot add notary to group parameters, no group parameters found.")

            val parametersMap = deserializeProperties(previous.parameters).toMap()
            val notary = memberInfoFactory.create(request.notary).notaryDetails
                ?: throw MembershipPersistenceException("Cannot add notary to group parameters - notary details not found.")
            val notaryServiceName = notary.serviceName.toString()
            val notaryServiceNumber = parametersMap.entries.firstOrNull { it.value == notaryServiceName }?.run {
                notaryServiceRegex.find(key)?.groups?.get(1)?.value?.toIntOrNull()
            }
            val entity = if (notaryServiceNumber != null) {
                // Add notary to existing notary service, or update notary with rotated keys
                updateExistingNotaryService(parametersMap, notary, notaryServiceNumber)
                    ?: return@transaction previous.epoch
            } else {
                // Add new notary service
                addNewNotaryService(parametersMap, notary)
            }

            em.persist(entity)

            entity.epoch
        }

        return PersistGroupParametersResponse(epoch)
    }

    private fun updateExistingNotaryService(
        currentParameters: Map<String, String>,
        notaryDetails: MemberNotaryDetails,
        notaryServiceNumber: Int
    ): GroupParametersEntity? {
        val notaryServiceName = notaryDetails.serviceName.toString()
        logger.info("Adding notary to group parameters under existing notary service '$notaryServiceName'.")
        notaryDetails.servicePlugin?.let {
            require(currentParameters[String.format(NOTARY_SERVICE_PLUGIN_KEY, notaryServiceNumber)].toString() == it) {
                throw MembershipPersistenceException("Cannot add notary to notary service " +
                        "'$notaryServiceName' - plugin types do not match.")
            }
        }
        val notaryKeys = currentParameters.entries
            .filter { it.key.startsWith(String.format(NOTARY_SERVICE_KEYS_PREFIX, notaryServiceNumber)) }
            .map { it.value }
        val startingIndex = notaryKeys.size
        val newKeys = notaryDetails.keys
            .map { keyEncodingService.encodeAsString(it.publicKey) }
            .filterNot { notaryKeys.contains(it) }
            .apply {
                if (isEmpty()) {
                    logger.warn(
                        "Group parameters not updated. Notary has no notary keys or " +
                                "its notary keys are already listed under notary service '$notaryServiceName'."
                    )
                    return null
                }
            }.mapIndexed { index, key ->
                KeyValuePair(
                    String.format(
                        NOTARY_SERVICE_KEYS_KEY,
                        notaryServiceNumber,
                        startingIndex + index
                    ),
                    key
                )
            }
        val newEpoch = currentParameters[EPOCH_KEY]!!.toInt() + 1
        val parametersWithUpdatedEpoch = with(currentParameters) {
            filterNot { listOf(EPOCH_KEY, MODIFIED_TIME_KEY).contains(it.key) }
                .map { KeyValuePair(it.key, it.value) } + listOf(
                KeyValuePair(EPOCH_KEY, newEpoch.toString()),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            )
        }
        return GroupParametersEntity(newEpoch, serializeProperties(KeyValuePairList(parametersWithUpdatedEpoch + newKeys)))
    }

    private fun addNewNotaryService(
        currentParameters: Map<String, String>,
        notaryDetails: MemberNotaryDetails
    ): GroupParametersEntity {
        val notaryServiceName = notaryDetails.serviceName.toString()
        logger.info("Adding notary to group parameters under new notary service '$notaryServiceName'.")
        requireNotNull(notaryDetails.servicePlugin) {
            throw MembershipPersistenceException("Cannot add notary to group parameters - notary plugin must be" +
                    " specified to create new notary service '$notaryServiceName'.")
        }
        val newNotaryServiceNumber = currentParameters
            .filter { notaryServiceRegex.matches(it.key) }.size
        val newService = notaryDetails.keys
            .mapIndexed { index, key ->
                KeyValuePair(
                    String.format(NOTARY_SERVICE_KEYS_KEY, newNotaryServiceNumber, index),
                    keyEncodingService.encodeAsString(key.publicKey)
                )
            } + listOf(
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, newNotaryServiceNumber), notaryServiceName),
            KeyValuePair(String.format(NOTARY_SERVICE_PLUGIN_KEY, newNotaryServiceNumber), notaryDetails.servicePlugin)
        )
        val newEpoch = currentParameters[EPOCH_KEY]!!.toInt() + 1
        val parametersWithUpdatedEpoch = with(currentParameters) {
            filterNot { listOf(EPOCH_KEY, MODIFIED_TIME_KEY).contains(it.key) }
                .map { KeyValuePair(it.key, it.value) } + listOf(
                KeyValuePair(EPOCH_KEY, newEpoch.toString()),
                KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
            )
        }
        return GroupParametersEntity(newEpoch, serializeProperties(KeyValuePairList(parametersWithUpdatedEpoch + newService)))
    }
}
