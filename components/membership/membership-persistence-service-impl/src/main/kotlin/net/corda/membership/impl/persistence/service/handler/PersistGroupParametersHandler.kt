package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda

internal class PersistGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupParameters, Unit>(persistenceHandlerServices) {
    private val keyValuePairListDeserializer =
        cordaAvroSerializationFactory.createAvroDeserializer({
            logger.error("Failed to deserialize key value pair list.")
        }, KeyValuePairList::class.java)

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    private fun serialize(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    private fun deserializeProperties(context: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(context) ?: throw MembershipPersistenceException(
            "Failed to deserialize key value pair list."
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: PersistGroupParameters
    ) {
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val serialisedGroupParameters = request.groupParameters.groupParameters.array()
            val groupParameters = deserializeProperties(serialisedGroupParameters)
            val epochFromRequest = groupParameters.toMap()[EPOCH_KEY]?.toInt()
                ?: throw MembershipPersistenceException("Cannot persist group parameters - epoch not found.")

            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder
                .createQuery(GroupParametersEntity::class.java)
            val root = queryBuilder
                .from(GroupParametersEntity::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))

            // if there is any data in the db, updated group parameters epoch should always be
            // larger than the existing group parameters epoch
            with(em.createQuery(query).setMaxResults(1).resultList) {
                singleOrNull()?.epoch?.let {
                    require(epochFromRequest > it) {
                        throw MembershipPersistenceException(
                            "Group parameters with epoch=$epochFromRequest already exist."
                        )
                    }
                }
            }

            em.persist(
                GroupParametersEntity(
                    epoch = epochFromRequest,
                    parameters = serialisedGroupParameters,
                    signaturePublicKey = request.groupParameters.mgmSignature?.publicKey?.array(),
                    signatureContext = request.groupParameters.mgmSignature?.context?.let { serialize(it) },
                    signatureContent = request.groupParameters.mgmSignature?.bytes?.array()
                )
            )
        }
    }
}
