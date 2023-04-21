package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager

internal class ActivateMemberHandler(
    private val persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<ActivateMember, ActivateMemberResponse>(persistenceHandlerServices) {
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
        SuspensionActivationEntityOperations(clock, keyValuePairListDeserializer, keyValuePairListSerializer)

    override fun invoke(context: MembershipRequestContext, request: ActivateMember): ActivateMemberResponse {
        val (updatedMemberInfo, updatedGroupParameters) = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val currentMemberInfo = suspensionActivationEntityOperations.findMember(
                em,
                request.activatedMember,
                context.holdingIdentity.groupId,
                request.serialNumber,
                MEMBER_STATUS_SUSPENDED
            )
            val currentMgmContext = keyValuePairListDeserializer.deserialize(currentMemberInfo.mgmContext)
                ?: throw MembershipPersistenceException("Failed to deserialize the MGM-provided context.")

            val updatedMemberInfo = suspensionActivationEntityOperations.updateStatus(
                em,
                request.activatedMember,
                context.holdingIdentity,
                currentMemberInfo,
                currentMgmContext,
                MEMBER_STATUS_ACTIVE
            )
            val updatedGroupParameters = if (memberInfoFactory.create(updatedMemberInfo).isNotary()) {
                updateGroupParameters(em, updatedMemberInfo)
            } else {
                null
            }

            updatedMemberInfo to updatedGroupParameters
        }
        return ActivateMemberResponse(updatedMemberInfo, updatedGroupParameters)
    }

    private fun updateGroupParameters(em: EntityManager, memberInfo: PersistentMemberInfo): SignedGroupParameters {
        val notaryHandler = AddNotaryToGroupParametersHandler(persistenceHandlerServices)
        return notaryHandler.addNotaryToGroupParameters(em, memberInfo)
    }
}
