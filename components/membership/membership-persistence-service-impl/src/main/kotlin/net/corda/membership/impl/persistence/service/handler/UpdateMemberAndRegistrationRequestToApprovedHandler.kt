package net.corda.membership.impl.persistence.service.handler

import javax.persistence.LockModeType
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToApproved
import net.corda.data.membership.db.response.query.UpdateMemberAndRegistrationRequestResponse
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.virtualnode.toCorda

internal class UpdateMemberAndRegistrationRequestToApprovedHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<
    UpdateMemberAndRegistrationRequestToApproved,
    UpdateMemberAndRegistrationRequestResponse
    >(persistenceHandlerServices) {
    override val operation = UpdateMemberAndRegistrationRequestToApproved::class.java

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
        logger.info(
            "Update member and registration request with registration ID ${request.registrationId} to approved.",
        )
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            val currentNonPendingMemberStatus = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(request.member.groupId, request.member.x500Name, false),
                LockModeType.PESSIMISTIC_WRITE,
            )?.status
            val newStatus = when (currentNonPendingMemberStatus) {
                MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED -> currentNonPendingMemberStatus
                else -> MEMBER_STATUS_ACTIVE
            }
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
                        KeyValuePair(it.key, newStatus)
                    } else {
                        it
                    }
                }
            )

            val serializedMgmContext = wrapWithNullErrorHandling({
                MembershipPersistenceException("Can not serialize the mgm context", it)
            }) {
                keyValuePairListSerializer.serialize(mgmContext)
            }

            em.merge(
                MemberInfoEntity(
                    member.groupId,
                    member.memberX500Name,
                    false,
                    newStatus,
                    now,
                    member.memberContext,
                    member.memberSignatureKey,
                    member.memberSignatureContent,
                    member.memberSignatureSpec,
                    serializedMgmContext,
                    member.serialNumber,
                    isDeleted = false
                )
            )

            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: throw MembershipPersistenceException("Could not find registration request: ${request.registrationId}")
            if (!registrationRequest.status.toStatus().canMoveToStatus(RegistrationStatus.APPROVED)) {
                throw MembershipPersistenceException(
                    "Registration request ${request.registrationId} has status ${registrationRequest.status} and can not be approved"
                )
            }
            registrationRequest.status = RegistrationStatus.APPROVED.name
            registrationRequest.lastModified = now

            UpdateMemberAndRegistrationRequestResponse(
                memberInfoFactory.createPersistentMemberInfo(
                    context.holdingIdentity,
                    member.memberContext,
                    serializedMgmContext,
                    member.memberSignatureKey,
                    member.memberSignatureContent,
                    member.memberSignatureSpec,
                )
            )
        }
    }
}
