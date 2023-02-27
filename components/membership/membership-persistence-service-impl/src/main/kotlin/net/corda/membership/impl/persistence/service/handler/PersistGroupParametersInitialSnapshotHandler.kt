package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistGroupParametersInitialSnapshotHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupParametersInitialSnapshot, PersistGroupParametersResponse>(persistenceHandlerServices) {
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

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to serialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    private fun deserializeProperties(content: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(content) ?: throw MembershipPersistenceException(
            "Failed to deserialize key value pair list."
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: PersistGroupParametersInitialSnapshot
    ): PersistGroupParametersResponse {
        val activePlatformVersion = platformInfoProvider.activePlatformVersion.toString()
        val persistedGroupParameters = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val currentGroupParameters = em.find(GroupParametersEntity::class.java, 1, LockModeType.PESSIMISTIC_WRITE)?.let {
                keyValuePairListDeserializer.deserialize(it.parameters)
            }
            currentGroupParameters?.let {
                if (it.get(MPV_KEY) != activePlatformVersion) {
                    throw MembershipPersistenceException("Group parameters already exists with a different platform version.")
                }
                return@transaction currentGroupParameters
            }

            // Create initial snapshot of group parameters.
            val groupParameters = KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "1"),
                    KeyValuePair(MPV_KEY, activePlatformVersion),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
                )
            )

            val currentGroupParameters = em.find(GroupParametersEntity::class.java, 1, LockModeType.PESSIMISTIC_WRITE)
            if (currentGroupParameters != null) {
                val currentParameters = deserializeProperties(currentGroupParameters.parameters).toMap()
                if (currentParameters.removeTime() != groupParameters.toMap().removeTime()) {
                    throw MembershipPersistenceException(
                        "Group parameters initial snapshot already exist with different parameters."
                    )
                } else {
                    return@transaction groupParameters
                }
            }
            val entity = GroupParametersEntity(
                epoch = 1,
                parameters = serializeProperties(groupParameters),
            )
            em.persist(entity)

            groupParameters
        }

        return PersistGroupParametersResponse(persistedGroupParameters)
    }

    private fun Map<String, String>.removeTime(): Map<String, String>  = this.filterKeys { it != MODIFIED_TIME_KEY }
}
