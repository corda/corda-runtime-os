package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.exceptions.NotFoundEntityPersistenceException
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toSortedMap
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager
import javax.persistence.LockModeType

internal class AddNotaryToGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<AddNotaryToGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private companion object {
        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
    }

    override val operation = AddNotaryToGroupParameters::class.java
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }

    private val deserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    private val notaryUpdater = GroupParametersNotaryUpdater(keyEncodingService, clock)

    private fun getLatestMemberList(entityManager: EntityManager, notary: MemberInfo): Collection<MemberInfo> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val memberQueryBuilder = criteriaBuilder.createQuery(MemberInfoEntity::class.java)
        val root = memberQueryBuilder.from(MemberInfoEntity::class.java)
        val memberQuery = memberQueryBuilder.select(root)
            .where(
                criteriaBuilder.equal(root.get<String>("status"), MEMBER_STATUS_ACTIVE),
                criteriaBuilder.notEqual(root.get<String>("memberX500Name"), notary.name.toString())
            )
        return entityManager.createQuery(memberQuery).setLockMode(LockModeType.PESSIMISTIC_WRITE).resultList.map {
            memberInfoFactory.createMemberInfo(
                deserializer.deserializeKeyValuePairList(it.memberContext).toSortedMap(),
                deserializer.deserializeKeyValuePairList(it.mgmContext).toSortedMap(),
            )
        }
    }

    private fun checkAgainstLatestMemberList(notary: MemberInfo, notaryServiceName: String, members: Collection<MemberInfo>) {
        members.firstOrNull { it.notaryDetails?.serviceName.toString() == notaryServiceName }?.let {
            require(it.name == notary.name && it.serial < notary.serial) {
                throw MembershipPersistenceException(
                    "Cannot add notary to group parameters - notary service '$notaryServiceName' already exists."
                )
            }
        }
        require(members.none { it.name.toString() == notaryServiceName }) {
            throw MembershipPersistenceException("There is a virtual node having the same name as the notary service $notaryServiceName.")
        }
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: AddNotaryToGroupParameters
    ): PersistGroupParametersResponse {
        val persistedGroupParameters = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            addNotaryToGroupParameters(em, request.notary)
        }
        return PersistGroupParametersResponse(persistedGroupParameters)
    }

    internal fun addNotaryToGroupParameters(
        em: EntityManager,
        notaryMemberInfo: PersistentMemberInfo
    ): SignedGroupParameters {
        val criteriaBuilder = em.criteriaBuilder
        val queryBuilder = criteriaBuilder.createQuery(GroupParametersEntity::class.java)
        val root = queryBuilder.from(GroupParametersEntity::class.java)
        val query = queryBuilder
            .select(root)
            .orderBy(criteriaBuilder.desc(root.get<String>("epoch")))
        val previous = em.createQuery(query)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .setMaxResults(1)
        if (previous.resultList.isEmpty()) {
            throw NotFoundEntityPersistenceException(
                "Cannot add notary to group parameters, no group parameters found."
            )
        }

        val parametersMap = deserializer.deserializeKeyValuePairList(previous.singleResult.parameters).toMap()
        val notaryInfo = memberInfoFactory.createMemberInfo(notaryMemberInfo)
        val notary = notaryInfo.notaryDetails
            ?: throw NotFoundEntityPersistenceException(
                "Cannot add notary to group parameters - notary details not found."
            )
        val notaryServiceName = notary.serviceName.toString()
        val notaryServiceNumber = parametersMap.entries.firstOrNull { it.value == notaryServiceName }?.run {
            notaryServiceRegex.find(key)?.groups?.get(1)?.value?.toIntOrNull()
        }
        val otherMembers = getLatestMemberList(em, notaryInfo)
        checkAgainstLatestMemberList(notaryInfo, notaryServiceName, otherMembers)
        val (epoch, groupParameters) = if (notaryServiceNumber != null) {
            // Add notary to existing notary service, or update notary with rotated keys
            val currentProtocolVersions = otherMembers.filter {
                it.notaryDetails?.serviceName.toString() == notaryServiceName &&
                    it.name != notaryInfo.name &&
                    it.status == MEMBER_STATUS_ACTIVE
            }.map {
                it.notaryDetails!!.serviceProtocolVersions.toHashSet()
            }.reduceOrNull { acc, it -> acc.apply { retainAll(it) } } ?: emptySet()

            notaryUpdater.updateExistingNotaryService(
                parametersMap,
                notary,
                notaryServiceNumber,
                currentProtocolVersions
            ).apply {
                first ?: return previous.singleResult.toAvro()
            }
        } else {
            // Add new notary service
            notaryUpdater.addNewNotaryService(
                parametersMap,
                notary,
            )
        }
        // Only an MGM should be calling this function and so a signature is not set since it's signed when
        // distributed.
        return GroupParametersEntity(
            epoch = epoch!!,
            parameters = keyValuePairListSerializer.serializeKeyValuePairList(groupParameters!!),
            signaturePublicKey = null,
            signatureContent = null,
            signatureSpec = null
        ).also {
            em.persist(it)
        }.toAvro()
    }
}
