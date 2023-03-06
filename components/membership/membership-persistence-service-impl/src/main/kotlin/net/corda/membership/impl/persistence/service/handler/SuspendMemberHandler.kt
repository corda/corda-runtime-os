package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class SuspendMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<SuspendMember, Unit>(persistenceHandlerServices) {

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

    override fun invoke(context: MembershipRequestContext, request: SuspendMember)  {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            val member = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(context.holdingIdentity.groupId, request.suspendedMember, false),
                LockModeType.OPTIMISTIC_FORCE_INCREMENT
            ) ?: throw MembershipPersistenceException("Member '${request.suspendedMember}' does not exist.")
            request.serialNumber?.let {
                require(member.serialNumber == it) {
                    throw MembershipPersistenceException("The provided serial number corresponds to an older version " +
                            "of MemberInfo for member '${request.suspendedMember}'.")
                }
            }
            require(member.status == MEMBER_STATUS_ACTIVE) {
                throw MembershipPersistenceException("Member '${request.suspendedMember}' cannot be suspended because" +
                        " it has status '${member.status}'.")
            }
            val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
                ?: throw MembershipPersistenceException("Failed to extract the MGM-provided context.")
            val mgmContext = KeyValuePairList(
                currentMgmContext.items.map {
                    when (it.key) {
                        STATUS -> KeyValuePair(it.key, MEMBER_STATUS_SUSPENDED)
                        MODIFIED_TIME -> KeyValuePair(it.key, now.toString())
                        SERIAL -> KeyValuePair(it.key, (request.serialNumber + 1).toString())
                        else -> it
                    }
                }
            )
            val serializedMgmContext = keyValuePairListSerializer.serialize(mgmContext)
                ?: throw MembershipPersistenceException("Failed to serialize the MGM-provided context.")

            em.merge(
                MemberInfoEntity(
                    member.groupId,
                    member.memberX500Name,
                    false,
                    MEMBER_STATUS_SUSPENDED,
                    now,
                    member.memberContext,
                    serializedMgmContext,
                    request.serialNumber
                )
            )
        }
    }
}
