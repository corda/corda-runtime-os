package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda

internal class PersistGroupParametersInitialSnapshotHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupParametersInitialSnapshot, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: PersistGroupParametersInitialSnapshot
    ): PersistGroupParametersResponse {
        val epoch = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            // Create initial snapshot of group parameters.
            val groupParameters = KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "1"),
                    KeyValuePair(MPV_KEY, platformInfoProvider.activePlatformVersion.toString()),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
                )
            )
            val entity = GroupParametersEntity(
                epoch = 1,
                parameters = serializeProperties(groupParameters),
            )
            em.persist(entity)

            entity.epoch
        }

        return PersistGroupParametersResponse(epoch)
    }
}
