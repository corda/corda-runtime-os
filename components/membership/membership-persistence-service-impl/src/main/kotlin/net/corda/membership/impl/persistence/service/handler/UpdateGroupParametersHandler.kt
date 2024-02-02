package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.impl.persistence.service.RecoverableException
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARIES_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class UpdateGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateGroupParameters, PersistGroupParametersResponse>(
    persistenceHandlerServices
) {
    override val operation = UpdateGroupParameters::class.java

    private val serializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    private val deserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    private fun Map<String, String>.toKeyValuePairs() = entries.map { KeyValuePair(it.key, it.value) }

    override fun invoke(
        context: MembershipRequestContext,
        request: UpdateGroupParameters
    ): PersistGroupParametersResponse {
        val persistedGroupParameters = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(GroupParametersEntity::class.java)
            val root = queryBuilder.from(GroupParametersEntity::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))
            val previous = em.createQuery(query)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
            if (previous.resultList.isEmpty()) {
                throw MembershipPersistenceException(
                    "Failed to update group parameters - could not retrieve current set of group parameters."
                )
            }
            val previousParameters = previous.singleResult
            val parametersMap = deserializer.deserializeKeyValuePairList(previousParameters.parameters).toMap()

            val newEpoch = parametersMap[EPOCH_KEY]!!.toInt() + 1
            val newGroupParameters = KeyValuePairList(
                (request.update + parametersMap.filter { it.key.startsWith(NOTARIES_KEY) }).toKeyValuePairs() + listOf(
                    KeyValuePair(EPOCH_KEY, newEpoch.toString()), KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
                )
            )
            GroupParametersEntity(
                epoch = newEpoch,
                parameters = serializer.serializeKeyValuePairList(newGroupParameters),
                signaturePublicKey = null,
                signatureContent = null,
                signatureSpec = null
            ).also {
                em.persist(it)
            }.toAvro()
        }

        try {
            val parameters = groupParametersFactory.create(persistedGroupParameters)
            groupParametersWriterService.put(
                context.holdingIdentity.toCorda(),
                parameters,
            )
        } catch (e: Exception) {
            throw RecoverableException("Could not write the group parameters to the message bus.", e)
        }

        return PersistGroupParametersResponse(persistedGroupParameters)
    }
}
