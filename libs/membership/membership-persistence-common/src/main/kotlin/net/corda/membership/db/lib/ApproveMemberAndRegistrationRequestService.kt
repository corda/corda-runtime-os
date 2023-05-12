package net.corda.membership.db.lib

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.common.RegistrationStatus
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.db.lib.RegistrationStatusHelper.toStatus
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.registration.RegistrationStatusExt.canMoveToStatus
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import javax.persistence.EntityManager
import javax.persistence.LockModeType

class ApproveMemberAndRegistrationRequestService(
    private val clock: Clock,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) {
    private companion object {
        val logger = LoggerFactory.getLogger(ApproveMemberAndRegistrationRequestService::class.java)
    }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java,
        )
    }
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to deserialize key value pair list.")
        }
    }

    @Suppress("ThrowsCount")
    fun update(
        em: EntityManager,
        memberHoldingIdentity: HoldingIdentity,
        viewOwningMember: HoldingIdentity,
        registrationId: String,
    ): PersistentMemberInfo {
        val now = clock.instant()
        val member = em.find(
            MemberInfoEntity::class.java,
            MemberInfoEntityPrimaryKey(memberHoldingIdentity.groupId, memberHoldingIdentity.x500Name, true),
            LockModeType.PESSIMISTIC_WRITE,
        ) ?: throw MembershipPersistenceException("Could not find member: $memberHoldingIdentity")

        val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
            ?: throw MembershipPersistenceException("Can not extract the mgm context")
        val memberContext = keyValuePairListDeserializer.deserialize(member.memberContext)
            ?: throw MembershipPersistenceException("Can not extract the member context")
        val mgmContext = KeyValuePairList(
            currentMgmContext.items.map {
                if (it.key == STATUS) {
                    KeyValuePair(it.key, MEMBER_STATUS_ACTIVE)
                } else {
                    it
                }
            },
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
                member.memberSignatureKey,
                member.memberSignatureContent,
                member.memberSignatureSpec,
                serializedMgmContext,
                member.serialNumber,
            ),
        )

        val registrationRequest = em.find(
            RegistrationRequestEntity::class.java,
            registrationId,
            LockModeType.PESSIMISTIC_WRITE,
        ) ?: throw MembershipPersistenceException("Could not find registration request: $registrationId")
        if (!registrationRequest.status.toStatus().canMoveToStatus(RegistrationStatus.APPROVED)) {
            throw MembershipPersistenceException(
                "Registration request $registrationId has status ${registrationRequest.status} and can not be approved",
            )
        }
        registrationRequest.status = RegistrationStatus.APPROVED.name
        registrationRequest.lastModified = now

        return PersistentMemberInfo(
            viewOwningMember,
            memberContext,
            mgmContext,
        )
    }
}
