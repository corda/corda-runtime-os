package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.EntityManager
import javax.persistence.LockModeType

internal abstract class BaseSuspensionActivationHandler<REQUEST, RESPONSE>(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<REQUEST, RESPONSE>(persistenceHandlerServices) {

    protected val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }
    protected val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    }

    protected fun findMember(
        em: EntityManager,
        memberName: String,
        groupId: String,
        expectedSerial: Long?,
        expectedStatus: String
    ): MemberInfoEntity {
        val member = em.find(
            MemberInfoEntity::class.java,
            MemberInfoEntityPrimaryKey(groupId, memberName, false),
            LockModeType.PESSIMISTIC_WRITE
        ) ?: throw MembershipPersistenceException("Member '$memberName' does not exist.")
        expectedSerial?.let {
            require(member.serialNumber == it) {
                throw InvalidEntityUpdateException(
                    "The provided serial number '$expectedSerial' does not match the current version '${member.serialNumber}' of " +
                        "MemberInfo for member '$memberName'.")
            }
        }
        require(member.status == expectedStatus) {
            "This action cannot be performed on member '$memberName' because it has status '${member.status}'."
        }
        return member
    }

    @Suppress("LongParameterList")
    protected fun updateStatus(
        em: EntityManager,
        memberName: String,
        mgmHoldingIdentity: HoldingIdentity,
        currentMemberInfo: MemberInfoEntity,
        currentMgmContext: KeyValuePairList,
        newStatus: String
    ): PersistentMemberInfo {
        val now = clock.instant()
        val updatedSerial = currentMemberInfo.serialNumber + 1
        val mgmContext = KeyValuePairList(currentMgmContext.items.map {
            when (it.key) {
                STATUS -> KeyValuePair(it.key, newStatus)
                MODIFIED_TIME -> KeyValuePair(it.key, now.toString())
                SERIAL -> KeyValuePair(it.key, updatedSerial.toString())
                else -> it
            }
        })
        val serializedMgmContext = keyValuePairListSerializer.serialize(mgmContext)
            ?: throw MembershipPersistenceException("Failed to serialize the MGM-provided context.")

        em.merge(
            MemberInfoEntity(
                mgmHoldingIdentity.groupId,
                memberName,
                false,
                newStatus,
                now,
                currentMemberInfo.memberContext,
                currentMemberInfo.memberSignatureKey,
                currentMemberInfo.memberSignatureContent,
                currentMemberInfo.memberSignatureSpec,
                serializedMgmContext,
                updatedSerial
            )
        )

        return PersistentMemberInfo(
            mgmHoldingIdentity,
            keyValuePairListDeserializer.deserialize(currentMemberInfo.memberContext),
            mgmContext,
        )
    }

    @Suppress("ThrowsCount")
    fun changeMemberStatus(
        context: MembershipRequestContext,
        memberName: String,
        memberSerial: Long?,
        currentStatus: String,
        newStatus: String,
    ): PersistentMemberInfo {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val currentMemberInfo = findMember(em, memberName, context.holdingIdentity.groupId, memberSerial, currentStatus)

            val currentMgmContext = keyValuePairListDeserializer.deserialize(currentMemberInfo.mgmContext)
                ?: throw MembershipPersistenceException("Failed to deserialize the MGM-provided context.")
            updateStatus(em, memberName, context.holdingIdentity, currentMemberInfo, currentMgmContext, newStatus)
        }
    }
}