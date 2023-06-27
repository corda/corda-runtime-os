package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.impl.persistence.service.RecoverableException
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    override val operation = PersistGroupParameters::class.java
    private val deserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    override fun invoke(
        context: MembershipRequestContext,
        request: PersistGroupParameters
    ): PersistGroupParametersResponse {
        val persistedGroupParameters = transaction(context.holdingIdentity.toCorda().shortHash) { em ->

            // Find the current parameters
            val serialisedGroupParameters = request.groupParameters.groupParameters.array()
            val groupParameters = deserializer.deserializeKeyValuePairList(serialisedGroupParameters)
            val epochFromRequest = groupParameters.toMap()[EPOCH_KEY]?.toInt()
                ?: throw MembershipPersistenceException("Cannot persist group parameters - epoch not found.")
            val currentEntity = em.find(
                GroupParametersEntity::class.java,
                epochFromRequest,
                LockModeType.PESSIMISTIC_WRITE,
            )
            if (currentEntity != null) {
                val currentParameters = deserializer.deserializeKeyValuePairList(currentEntity.parameters).toMap()
                if (groupParameters.toMap() != currentParameters) {
                    throw MembershipPersistenceException(
                        "Group parameters with epoch=$epochFromRequest already exist with different parameters."
                    )
                }
            } else {
                GroupParametersEntity(
                    epoch = epochFromRequest,
                    parameters = serialisedGroupParameters,
                    signaturePublicKey = request.groupParameters.mgmSignature.publicKey.array(),
                    signatureContent = request.groupParameters.mgmSignature.bytes.array(),
                    signatureSpec = request.groupParameters.mgmSignatureSpec.signatureName
                ).also {
                    em.persist(it)
                }
            }

            // Find the latest parameters
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(GroupParametersEntity::class.java)
            val root = queryBuilder.from(GroupParametersEntity::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))
            em.createQuery(query)
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .resultList
                .first()
                .toAvro()
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
