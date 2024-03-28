package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupPolicy
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.lib.exceptions.ConflictPersistenceException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistGroupPolicyHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupPolicy, Unit>(persistenceHandlerServices) {
    override val operation = PersistGroupPolicy::class.java
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            { logger.error("Failed to deserialize key value pair list.") },
            KeyValuePairList::class.java
        )

    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return wrapWithNullErrorHandling({
            MembershipPersistenceException("Failed to serialize key value pair list.", it)
        }) {
            keyValuePairListSerializer.serialize(context)
        }
    }

    override fun invoke(context: MembershipRequestContext, request: PersistGroupPolicy) {
        if (request.version < 1) {
            throw MembershipPersistenceException(
                "Cannot update group policy: with version ${request.version} which is smaller than 1."
            )
        }

        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            em.find(GroupPolicyEntity::class.java, request.version, LockModeType.PESSIMISTIC_WRITE)?.let {
                val persistedProperties = keyValuePairListDeserializer.deserialize(it.properties)
                if (persistedProperties != request.properties) {
                    throw ConflictPersistenceException(
                        "Cannot update group policy: a group policy with version ${request.version} already exists."
                    )
                }
                return@transaction
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
