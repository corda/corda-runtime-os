package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.response.query.UpdateMemberAndRequestResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.virtualnode.toCorda

internal class UpdateMemberAndRegistrationRequestToApprovedHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<UpdateMemberAndRegistrationRequestToApproved, UpdateMemberAndRequestResponse>(persistenceHandlerServices) {

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: UpdateMemberAndRegistrationRequestToApproved,
    ): UpdateMemberAndRequestResponse {
        logger.info("Update member and registration request to approve.")
        return transaction(context.holdingIdentity.toCorda().id) { em ->
            val now = clock.instant()
            val member = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(request.member.groupId, request.member.x500Name)
            ) ?: throw MembershipPersistenceException("Could not find member: ${request.member}")

            member.status = MEMBER_STATUS_ACTIVE
            member.modifiedTime = now

            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId,
            ) ?: throw MembershipPersistenceException("Could not find registration request: ${request.registrationId}")
            registrationRequest.status = RegistrationStatus.APPROVED.name
            registrationRequest.lastModified = now

            UpdateMemberAndRequestResponse(
                PersistentMemberInfo(
                    context.holdingIdentity,
                    keyValuePairListDeserializer.deserialize(member.memberContext),
                    keyValuePairListDeserializer.deserialize(member.mgmContext),
                ),
            )
        }
    }
}
