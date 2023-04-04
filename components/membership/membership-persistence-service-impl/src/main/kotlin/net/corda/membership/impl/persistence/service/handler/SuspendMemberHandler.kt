package net.corda.membership.impl.persistence.service.handler

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
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.membership.lib.toSortedMap
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager
import javax.persistence.LockModeType

internal class SuspendMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BaseSuspensionActivationHandler<SuspendMember, SuspendMemberResponse>(persistenceHandlerServices) {
    private companion object {
        val notaryServiceRegex = NOTARY_SERVICE_NAME_KEY.format("([0-9]+)").toRegex()
    }
    private val notaryUpdater = GroupParametersNotaryUpdater(keyEncodingService, clock)
    private fun serializeProperties(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }
    override fun invoke(context: MembershipRequestContext, request: SuspendMember): SuspendMemberResponse {
        val (updatedMemberInfo, updatedGroupParameters) = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            logger.info("HERE!!!!!")
            val currentMemberInfo = findMember(
                em,
                request.suspendedMember,
                context.holdingIdentity.groupId,
                request.serialNumber,
                MEMBER_STATUS_ACTIVE
            )
            val currentMgmContext = keyValuePairListDeserializer.deserialize(currentMemberInfo.mgmContext)
                ?: throw MembershipPersistenceException("Failed to deserialize the MGM-provided context.")
            PersistentMemberInfo(
                HoldingIdentity(request.suspendedMember, context.holdingIdentity.groupId),
                keyValuePairListDeserializer.deserialize(currentMemberInfo.memberContext),
                currentMgmContext,
            )

            val updatedMemberInfo = updateStatus(
                em,
                request.suspendedMember,
                context.holdingIdentity.groupId,
                currentMemberInfo,
                currentMgmContext,
                MEMBER_STATUS_SUSPENDED
            )
            val updatedGroupParameters = if (memberInfoFactory.create(updatedMemberInfo).notaryDetails != null) {
                updateGroupParameters(em, updatedMemberInfo, HoldingIdentity(request.suspendedMember, context.holdingIdentity.groupId))
            } else {
                null
            }

            updatedMemberInfo to updatedGroupParameters
        }
        logger.info("DONE!!!!!")
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
            throw MembershipPersistenceException(
                "Cannot add notary to group parameters, no group parameters found."
            )
        }

        val parametersMap = keyValuePairListDeserializer.deserializeKeyValuePairList(previous.singleResult.parameters).toMap()
        val notaryInfo = memberInfoFactory.create(memberInfo)
        val notary = notaryInfo.notaryDetails
            ?: throw MembershipPersistenceException(
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
        val memberQuery = memberQueryBuilder.select(memberQueryBuilder.from(MemberInfoEntity::class.java))
        val members = em.createQuery(memberQuery)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .resultList.map {
                memberInfoFactory.create(
                    keyValuePairListDeserializer.deserializeKeyValuePairList(it.memberContext).toSortedMap(),
                    keyValuePairListDeserializer.deserializeKeyValuePairList(it.mgmContext).toSortedMap(),
                )
            }
        val otherMembersOfSameNotaryService = members.filter {
            it.notaryDetails?.serviceName.toString() == notaryServiceName &&
                    it.name != notaryInfo.name &&
                    it.status == MEMBER_STATUS_ACTIVE
        }.mapNotNull { it.notaryDetails }

        val (epoch, groupParameters) = if (otherMembersOfSameNotaryService.isEmpty()) {
            notaryUpdater.removeNotaryService(parametersMap, notaryServiceNumber)
        } else {
            notaryUpdater.removeNotaryFromExistingNotaryService(parametersMap, notary, notaryServiceNumber, otherMembersOfSameNotaryService)
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
