package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParametersInitialSnapshot
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.exceptions.ConflictPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistGroupParametersInitialSnapshotHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistGroupParametersInitialSnapshot, PersistGroupParametersResponse>(
    persistenceHandlerServices
) {
    override val operation = PersistGroupParametersInitialSnapshot::class.java
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

    override fun invoke(
        context: MembershipRequestContext,
        request: PersistGroupParametersInitialSnapshot
    ): PersistGroupParametersResponse {
        val persistedGroupParameters = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            // Create initial snapshot of group parameters.
            val groupParameters = KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "1"),
                    KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
                )
            )

            val currentGroupParameters = em.find(
                GroupParametersEntity::class.java,
                1,
                LockModeType.PESSIMISTIC_WRITE
            )
            if (currentGroupParameters != null) {
                val currentParameters =
                    deserializer.deserializeKeyValuePairList(currentGroupParameters.parameters).toMap()
                if (currentParameters.removeTime() != groupParameters.toMap().removeTime()) {
                    throw ConflictPersistenceException(
                        "Group parameters initial snapshot already exist with different parameters."
                    )
                } else {
                    return@transaction currentGroupParameters.toAvro()
                }
            }
            GroupParametersEntity(
                epoch = 1,
                parameters = serializer.serializeKeyValuePairList(groupParameters),
                signaturePublicKey = null,
                signatureContent = null,
                signatureSpec = null
            ).also {
                em.persist(it)
            }.toAvro()
        }

        return PersistGroupParametersResponse(persistedGroupParameters)
    }

    private fun Map<String, String>.removeTime(): Map<String, String> = this.filterKeys { it != MODIFIED_TIME_KEY }
}
