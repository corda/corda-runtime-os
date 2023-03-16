package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.MemberSignatureEntity
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.canMoveToStatus
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class UpdateMemberAndRegistrationRequestToApprovedHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<
    UpdateMemberAndRegistrationRequestToApproved,
    UpdateMemberAndRegistrationRequestResponse
    >(persistenceHandlerServices) {

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
            logger.error("Failed to deserialize key value pair list.")
        }
    }

    override fun invoke(
        context: MembershipRequestContext,
        request: UpdateMemberAndRegistrationRequestToApproved,
    ): UpdateMemberAndRegistrationRequestResponse {
        logger.info("Update member and registration request to approve.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            val member = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(request.member.groupId, request.member.x500Name, true),
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: throw MembershipPersistenceException("Could not find member: ${request.member}")
            val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
                ?: throw MembershipPersistenceException("Can not extract the mgm context")
            val mgmContext = KeyValuePairList(
                currentMgmContext.items.map {
                    if (it.key == STATUS) {
                        KeyValuePair(it.key, MEMBER_STATUS_ACTIVE)
                    } else {
                        it
                    }
                }
            )

            val serializedMgmContext = keyValuePairListSerializer.serialize(mgmContext)
                ?: throw MembershipPersistenceException("Can not serialize the mgm context")

            em.merge(
                MemberInfoEntity(
                    member.groupId,
                    member.memberX500Name,
                    false,
                    MEMBER_STATUS_ACTIVE,
                    now,
                    member.memberContext,
                    serializedMgmContext,
                    member.serialNumber
                )
            )

            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: throw MembershipPersistenceException("Could not find registration request: ${request.registrationId}")
            if(!registrationRequest.status.toStatus().canMoveToStatus(RegistrationStatus.APPROVED)) {
                throw MembershipPersistenceException(
                    "Registration request ${request.registrationId} has status ${registrationRequest.status} and can not be approved"
                )
            }
            registrationRequest.status = RegistrationStatus.APPROVED.name
            registrationRequest.lastModified = now

            val signature = em.find(
                MemberSignatureEntity::class.java,
                MemberInfoEntityPrimaryKey(request.member.groupId, request.member.x500Name, true),
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: throw MembershipPersistenceException("Could not find signature for member: ${request.member}")

            em.merge(
                MemberSignatureEntity(
                    member.groupId,
                    member.memberX500Name,
                    false,
                    signature.publicKey,
                    signature.context,
                    signature.content
                )
            )

            UpdateMemberAndRegistrationRequestResponse(
                PersistentMemberInfo(
                    context.holdingIdentity,
                    keyValuePairListDeserializer.deserialize(member.memberContext),
                    mgmContext,
                ),
            )
        }
    }
}
