package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistGroupPolicyHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupPolicy, Unit>(persistenceHandlerServices) {
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer (
            { logger.error("Failed to deserialize key value pair list.") },
            KeyValuePairList::class.java
        )
    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    override fun invoke(context: MembershipRequestContext, request: PersistGroupPolicy) {
        if (request.version < 1) {
            throw MembershipPersistenceException(
                "Cannot update group policy: with version ${request.version} which is smaller than 1."
            )
        }

        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(GroupPolicyEntity::class.java)
            val root = queryBuilder.from(GroupPolicyEntity::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.desc(root.get<String>("version")))

            val lastPersistedVersion = with(em.createQuery(query).setLockMode(LockModeType.PESSIMISTIC_WRITE).setMaxResults(1).resultList) {
                singleOrNull()?.let { persistedGroupPolicy ->
                    if (request.version == persistedGroupPolicy.version) {
                        val persistedProperties = keyValuePairListDeserializer.deserialize(persistedGroupPolicy.properties)
                        if (persistedProperties != request.properties) {
                            throw MembershipPersistenceException(
                                "Cannot update group policy: a group policy with version ${request.version} already exists."
                            )
                        }
                        return@transaction
                    }
                    persistedGroupPolicy.version
                } ?: 0
            }

            if (request.version != lastPersistedVersion + 1) {
                if (request.version > lastPersistedVersion) {
                    throw MembershipPersistenceException("Cannot update group policy: with version ${request.version}. No policy " +
                        "with version ${request.version - 1} exists.")
                } else {
                    throw MembershipPersistenceException("Cannot update group policy: with version ${request.version} smaller " +
                            "than the latest persisted version.")
                }
            }

            val entity = GroupPolicyEntity(
                version = request.version,
                createdAt = clock.instant(),
                properties = serializeProperties(request.properties),
            )
            em.persist(entity)
        }
    }
}
