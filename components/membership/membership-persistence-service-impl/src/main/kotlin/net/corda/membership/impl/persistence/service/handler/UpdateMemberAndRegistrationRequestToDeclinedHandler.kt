package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.UpdateMemberAndRegistrationRequestToDeclined
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.canMoveToStatus
import net.corda.membership.impl.persistence.service.handler.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class UpdateMemberAndRegistrationRequestToDeclinedHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<
        UpdateMemberAndRegistrationRequestToDeclined,
        Unit
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
        request: UpdateMemberAndRegistrationRequestToDeclined,
    ) {
        logger.info("Update member and registration request to declined.")
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(request.member.groupId, request.member.x500Name),
                LockModeType.PESSIMISTIC_WRITE,
            )?.let { member ->
                member.status = MemberInfoExtension.MEMBER_STATUS_DECLINED
                member.modifiedTime = now
                val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
                    ?: throw MembershipPersistenceException("Can not extract the mgm context")
                val mgmContext = KeyValuePairList(
                    currentMgmContext.items.map {
                        if (it.key == MemberInfoExtension.STATUS) {
                            KeyValuePair(it.key, MemberInfoExtension.MEMBER_STATUS_DECLINED)
                        } else {
                            it
                        }
                    }
                )
                member.mgmContext =
                    keyValuePairListSerializer.serialize(mgmContext)
                        ?: throw MembershipPersistenceException("Can not serialize the mgm context")
            } ?: logger.info(
                "Member information does not exist in database for ${request.member.x500Name} in group " +
                        "${request.member.groupId}. Skipping setting status to declined as it might not have been " +
                        "created yet."
            )

            val registrationRequest = em.find(
                RegistrationRequestEntity::class.java,
                request.registrationId,
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: throw MembershipPersistenceException("Could not find registration request: ${request.registrationId}")
            if (!registrationRequest.status.toStatus().canMoveToStatus(RegistrationStatus.DECLINED)) {
                throw MembershipPersistenceException(
                    "Registration request ${request.registrationId} has status ${registrationRequest.status} and can not be declined"
                )
            }
            registrationRequest.status = RegistrationStatus.DECLINED.name
            registrationRequest.lastModified = now
        }
    }
}
