package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
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
import javax.persistence.LockModeType
import javax.persistence.PersistenceException

internal abstract class BaseSuspensionActivationHandler<REQUEST, RESPONSE>(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<REQUEST, RESPONSE>(persistenceHandlerServices) {

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

    fun changeMemberStatus(
        context: MembershipRequestContext,
        memberName: String,
        memberSerial: Long?,
        currentStatus: String,
        newStatus: String,
    ): PersistentMemberInfo {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            val member = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(context.holdingIdentity.groupId, memberName, false),
                LockModeType.PESSIMISTIC_WRITE
            ) ?: throw MembershipPersistenceException("Member '$memberName' does not exist.")
            memberSerial?.let {
                require(member.serialNumber == it) {
                    throw InvalidEntityUpdateException(
                        "The provided serial number does not match the current version of MemberInfo for member '$memberName'."
                    )
                }
            }
            require(member.status == currentStatus) {
                "This action cannot be performed on member '$memberName' because it has status '${member.status}'."
            }
            val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
                ?: throw MembershipPersistenceException("Failed to deserialize the MGM-provided context.")
            val mgmContext = KeyValuePairList(currentMgmContext.items.map {
                when (it.key) {
                    STATUS -> KeyValuePair(it.key, newStatus)
                    MODIFIED_TIME -> KeyValuePair(it.key, now.toString())
                    SERIAL -> KeyValuePair(it.key, (member.serialNumber + 1).toString())
                    else -> it
                }
            })
            val serializedMgmContext = keyValuePairListSerializer.serialize(mgmContext)
                ?: throw MembershipPersistenceException("Failed to serialize the MGM-provided context.")

            em.merge(
                MemberInfoEntity(
                    member.groupId,
                    member.memberX500Name,
                    member.isPending,
                    newStatus,
                    now,
                    member.memberContext,
                    serializedMgmContext,
                    member.serialNumber
                )
            )

            PersistentMemberInfo(
                context.holdingIdentity,
                keyValuePairListDeserializer.deserialize(member.memberContext),
                mgmContext,
            )
        }
    }
}