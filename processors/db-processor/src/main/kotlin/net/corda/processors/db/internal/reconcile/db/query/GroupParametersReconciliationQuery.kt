package net.corda.processors.db.internal.reconcile.db.query

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import javax.persistence.EntityManager

/**
 * Query implementation for reconciling the group parameters data per virtual node vault database.
 *
 * @param cordaAvroSerializationFactory serialization factory used to deserialise the persisted group parameters.
 * @param groupParametersFactory factory use the build a group parameters object from the persisted group parameters.
 */
class GroupParametersReconciliationQuery(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val groupParametersFactory: GroupParametersFactory
) : VaultReconciliationQuery<HoldingIdentity, GroupParameters> {

    private companion object {
        val logger = contextLogger()
    }

    private val cordaAvroDeserializer = cordaAvroSerializationFactory.createAvroDeserializer(
        { logger.warn("Could not deserialize group parameters from the database entity.") },
        KeyValuePairList::class.java
    )

    /**
     * Query for the latest group parameters for a virtual node based on the epoch value. The highest epoch value
     * is the latest.
     */
    override fun invoke(
        vnodeInfo: VirtualNodeInfo,
        em: EntityManager
    ): Collection<VersionedRecord<HoldingIdentity, GroupParameters>> {
        val criteriaBuilder = em.criteriaBuilder
        val queryBuilder = criteriaBuilder.createQuery(GroupParametersEntity::class.java)
        val root = queryBuilder.from(GroupParametersEntity::class.java)
        val query = queryBuilder.select(root)
            .orderBy(
                criteriaBuilder.desc(root.get<String>("epoch"))
            )

        val entity = em.createQuery(query)
            .setMaxResults(1)
            .singleResult

        val deserializedParams = cordaAvroDeserializer.deserialize(entity.parameters)
            ?: throw CordaRuntimeException("Could not deserialize group parameters from the database.")

        return listOf(
            object : VersionedRecord<HoldingIdentity, GroupParameters> {
                override val version = entity.epoch
                override val isDeleted = false
                override val key = vnodeInfo.holdingIdentity
                override val value by lazy {
                    groupParametersFactory.create(deserializedParams.items.associate { it.key to it.value })
                }
            }
        )
    }
}