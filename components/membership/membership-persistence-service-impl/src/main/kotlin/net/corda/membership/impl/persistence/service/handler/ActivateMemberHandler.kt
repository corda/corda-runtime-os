package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedGroupParameters
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.response.command.ActivateMemberResponse
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.utilities.time.Clock
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager
import javax.persistence.PessimisticLockException

internal class ActivateMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
    private val addNotaryToGroupParametersHandler: AddNotaryToGroupParametersHandler =
        AddNotaryToGroupParametersHandler(persistenceHandlerServices),
    suspensionActivationEntityOperationsFactory:
    (clock: Clock, serializer: CordaAvroSerializer<KeyValuePairList>)
    -> SuspensionActivationEntityOperations =
        { clock: Clock, serializer: CordaAvroSerializer<KeyValuePairList>
            ->
            SuspensionActivationEntityOperations(clock, serializer, persistenceHandlerServices.memberInfoFactory)
        }
) : BasePersistenceHandler<ActivateMember, ActivateMemberResponse>(persistenceHandlerServices) {
    override val operation = ActivateMember::class.java
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
            val updatedGroupParameters = if (memberInfoFactory.createMemberInfo(updatedMemberInfo).isNotary()) {
                logger.info("Activating notary member ${context.holdingIdentity}.")
                updateGroupParameters(em, updatedMemberInfo)
            } else {
                logger.info("Activating member ${context.holdingIdentity}.")
                null
            }

            updatedMemberInfo to updatedGroupParameters
        }
        return ActivateMemberResponse(updatedMemberInfo, updatedGroupParameters)
    }

    private fun updateGroupParameters(em: EntityManager, memberInfo: PersistentMemberInfo): SignedGroupParameters {
        return try {
            addNotaryToGroupParametersHandler.addNotaryToGroupParameters(em, memberInfo)
        } catch (e: PessimisticLockException) {
            throw InvalidEntityUpdateException(
                "Could not update member group parameters: ${e.message}",
            )
        }
    }
}
