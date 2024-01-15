package net.corda.membership.impl.persistence.service.handler

import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateStaticNetworkInfo
import net.corda.data.membership.db.response.query.StaticNetworkInfoQueryResponse
import net.corda.membership.datamodel.StaticNetworkInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.network.writer.staticnetwork.StaticNetworkInfoMappingUtils.toAvro
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import javax.persistence.LockModeType

internal class UpdateStaticNetworkInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateStaticNetworkInfo, StaticNetworkInfoQueryResponse>(persistenceHandlerServices) {
    override val operation = UpdateStaticNetworkInfo::class.java

    private val deserializer = cordaAvroSerializationFactory.createAvroDeserializer({
        logger.error("Failed to deserialize KeyValuePairList.")
    }, KeyValuePairList::class.java)

    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<KeyValuePairList> {
        logger.error("Failed to serialize KeyValuePairList.")
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: UpdateStaticNetworkInfo
    ): StaticNetworkInfoQueryResponse {
        val groupId = request.info.groupId
        logger.info("Attempting to update the static network information for network $groupId")
        return transaction { em ->
            val entity = em.find(
                StaticNetworkInfoEntity::class.java,
                groupId,
                LockModeType.PESSIMISTIC_WRITE
            ) ?: throw MembershipPersistenceException(
                "No existing static network configuration. Cannot update."
            )

            val persistedVersion = entity.version
            val proposedVersion = request.info.version

            val persistedGroupParams = deserializer.deserializeKeyValuePairList(entity.groupParameters)
            val proposedGroupParams = request.info.groupParameters

            if (persistedVersion == proposedVersion) {
                // Update persisted group params.
                if (!groupParametersAreEqual(persistedGroupParams, proposedGroupParams)) {
                    entity.groupParameters = wrapWithNullErrorHandling({
                        MembershipPersistenceException("Could not serialize new group parameters.", it)
                    }) {
                        serializer.serialize(proposedGroupParams)
                    }
                    em.merge(entity)
                    em.flush()
                }
            } else {
                if (persistedVersion == proposedVersion + 1 &&
                    groupParametersAreEqual(persistedGroupParams, proposedGroupParams)
                ) {
                    logger.info(
                        "Attempted to update the group parameters for a static network but they are " +
                            "unchanged. Returning the previously persisted version."
                    )
                } else {
                    throw MembershipPersistenceException(
                        "Current persisted version of the static network information does not match the version in " +
                            "the request to update."
                    )
                }
            }

            StaticNetworkInfoQueryResponse(entity.toAvro(deserializer))
        }
    }

    private fun groupParametersAreEqual(params1: KeyValuePairList, params2: KeyValuePairList): Boolean {
        return params1.normaliseForComparison() == params2.normaliseForComparison()
    }

    private fun KeyValuePairList.normaliseForComparison(): KeyValuePairList {
        return KeyValuePairList(
            items.filter { it.key != MODIFIED_TIME_KEY }
        )
    }
}
