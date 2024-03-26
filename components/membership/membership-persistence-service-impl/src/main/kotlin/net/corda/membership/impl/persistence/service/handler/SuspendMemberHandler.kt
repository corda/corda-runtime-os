package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.response.command.SuspendMemberResponse
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.exceptions.NotFoundEntityPersistenceException
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toSortedMap
import net.corda.utilities.mapNotNull
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager
import javax.persistence.LockModeType
import javax.persistence.PessimisticLockException
import kotlin.streams.toList

internal class SuspendMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
    private val notaryUpdater: GroupParametersNotaryUpdater =
        GroupParametersNotaryUpdater(persistenceHandlerServices.keyEncodingService, persistenceHandlerServices.clock),
    suspensionActivationEntityOperationsFactory:
    (clock: Clock, serializer: CordaAvroSerializer<KeyValuePairList>)
    -> SuspensionActivationEntityOperations =
        { clock: Clock, serializer: CordaAvroSerializer<KeyValuePairList>
            ->
            SuspensionActivationEntityOperations(clock, serializer, persistenceHandlerServices.memberInfoFactory)
        }
) : BasePersistenceHandler<SuspendMember, SuspendMemberResponse>(persistenceHandlerServices) {
    override val operation = SuspendMember::class.java
    private companion object {
        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
    }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    }
    private val suspensionActivationEntityOperations =
        suspensionActivationEntityOperationsFactory(clock, keyValuePairListSerializer)

    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return wrapWithNullErrorHandling({
            MembershipPersistenceException("Failed to serialize key value pair list.", it)
        }) {
            keyValuePairListSerializer.serialize(context)
        }
    }

    override fun invoke(context: MembershipRequestContext, request: SuspendMember): SuspendMemberResponse {
        val (updatedMemberInfo, updatedGroupParameters) = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val currentMemberInfo = suspensionActivationEntityOperations.findMember(
                em,
                request.suspendedMember,
                context.holdingIdentity.groupId,
                request.serialNumber,
                MEMBER_STATUS_ACTIVE
            )
            val currentMgmContext = keyValuePairListDeserializer.deserialize(currentMemberInfo.mgmContext)
                ?: throw MembershipPersistenceException("Failed to deserialize the MGM-provided context.")

            val updatedMemberInfo = suspensionActivationEntityOperations.updateStatus(
                em,
                request.suspendedMember,
                context.holdingIdentity,
                currentMemberInfo,
                currentMgmContext,
                MEMBER_STATUS_SUSPENDED
            )
            val updatedGroupParameters = if (memberInfoFactory.createMemberInfo(updatedMemberInfo).isNotary()) {
                logger.info("Suspending notary member ${context.holdingIdentity}.")
                try {
                    updateGroupParameters(
                        em,
                        updatedMemberInfo,
                        HoldingIdentity(
                            request.suspendedMember,
                            context.holdingIdentity.groupId
                        ),
                    )
                } catch (e: PessimisticLockException) {
                    throw InvalidEntityUpdateException(
                        "Could not update member group parameters: ${e.message}",
                    )
                }
            } else {
                logger.info("Suspending member ${context.holdingIdentity}.")
                null
            }

            updatedMemberInfo to updatedGroupParameters
        }
        return SuspendMemberResponse(updatedMemberInfo, updatedGroupParameters)
    }

    private fun updateGroupParameters(
        em: EntityManager,
        memberInfo: PersistentMemberInfo,
        holdingIdentity: HoldingIdentity
    ): SignedGroupParameters? {
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

        val parametersMap =
            keyValuePairListDeserializer.deserializeKeyValuePairList(previous.singleResult.parameters).toMap()
        val notaryInfo = memberInfoFactory.createMemberInfo(memberInfo)
        val notary = notaryInfo.notaryDetails
            ?: throw NotFoundEntityPersistenceException(
                "Cannot add notary to group parameters - notary details not found."
            )
        val notaryServiceName = notary.serviceName.toString()
        val notaryServiceNumber = parametersMap.entries.firstOrNull { it.value == notaryServiceName }?.run {
            notaryServiceRegex.find(key)?.groups?.get(1)?.value?.toIntOrNull()
        }
        if (notaryServiceNumber == null) {
            logger.warn("Notary `$holdingIdentity` has not updated the group parameters ")
            return null
        }
        val memberQueryBuilder = criteriaBuilder.createQuery(MemberInfoEntity::class.java)
        val memberRoot = memberQueryBuilder.from(MemberInfoEntity::class.java)
        val memberQuery = memberQueryBuilder.select(memberRoot)
            .where(
                criteriaBuilder.equal(memberRoot.get<String>("status"), MEMBER_STATUS_ACTIVE),
                criteriaBuilder.notEqual(memberRoot.get<String>("memberX500Name"), notaryInfo.name.toString())
            )
        val otherMembers = em.createQuery(memberQuery)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultStream.map {
                memberInfoFactory.createMemberInfo(
                    keyValuePairListDeserializer.deserializeKeyValuePairList(it.memberContext).toSortedMap(),
                    keyValuePairListDeserializer.deserializeKeyValuePairList(it.mgmContext).toSortedMap(),
                )
            }
        val otherMembersOfSameNotaryService = otherMembers.filter { otherMemberInfo ->
            otherMemberInfo.notaryDetails?.serviceName.toString() == notaryServiceName
        }.mapNotNull { it.notaryDetails }.toList()

        val (epoch, groupParameters) = if (otherMembersOfSameNotaryService.isEmpty()) {
            notaryUpdater.removeNotaryService(parametersMap, notaryServiceNumber)
        } else {
            notaryUpdater.removeNotaryFromExistingNotaryService(
                parametersMap,
                notary,
                notaryServiceNumber,
                otherMembersOfSameNotaryService
            )
        }
        // Only an MGM should be calling this function and so a signature is not set since it's signed when
        // distributed.
        return GroupParametersEntity(
            epoch = epoch!!,
            parameters = serializeProperties(groupParameters!!),
            signaturePublicKey = null,
            signatureContent = null,
            signatureSpec = null
        ).also {
            em.persist(it)
        }.toAvro()
    }
}
