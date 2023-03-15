package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.data.membership.db.response.query.GroupPolicyQueryResponse
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.virtualnode.toCorda

internal class QueryGroupPolicyHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<QueryGroupPolicy, GroupPolicyQueryResponse>(persistenceHandlerServices) {
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: QueryGroupPolicy
    ): GroupPolicyQueryResponse {
        logger.info("Searching for group policy for identity ${context.holdingIdentity}.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val result = em.createQuery(
                "SELECT g FROM ${GroupPolicyEntity::class.simpleName} g ORDER BY version DESC",
                GroupPolicyEntity::class.java
            ).resultList
            if(result.isEmpty()) {
                logger.warn("There was no persisted group policy found for identity ${context.holdingIdentity}. " +
                        "Returning empty properties.")
                GroupPolicyQueryResponse(KeyValuePairList(emptyList<KeyValuePair>()), 0)
            } else {
                logger.info("Persisted group policy was found for identity ${context.holdingIdentity}. " +
                        "Returning properties.")
                GroupPolicyQueryResponse(keyValuePairListDeserializer.deserialize(result.first().properties), result.first().version)
            }
        }
    }

}