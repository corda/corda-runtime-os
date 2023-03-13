package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.response.command.SuspendMemberResponse
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
import javax.persistence.PersistenceException

internal class SuspendMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<SuspendMember, SuspendMemberResponse>(persistenceHandlerServices) {

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

    override fun invoke(context: MembershipRequestContext, request: SuspendMember): SuspendMemberResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            val suspendedMember = request.suspendedMember
            val member = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(context.holdingIdentity.groupId, suspendedMember, false),
                LockModeType.OPTIMISTIC_FORCE_INCREMENT
            ) ?: throw MembershipPersistenceException("Member '$suspendedMember' does not exist.")
            request.serialNumber?.let {
                require(member.serialNumber == it) {
                    throw PersistenceException("The provided serial number does not match the current " +
                            "version of MemberInfo for member '$suspendedMember'.")
                }
            }
            require(member.status == MEMBER_STATUS_ACTIVE) {
                "Member '$suspendedMember' cannot be suspended because it has status '${member.status}'."
            }
            val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
                ?: throw MembershipPersistenceException("Failed to deserialize the MGM-provided context.")
            val mgmContext = KeyValuePairList(
                currentMgmContext.items.map {
                    when (it.key) {
                        STATUS -> KeyValuePair(it.key, MEMBER_STATUS_SUSPENDED)
                        MODIFIED_TIME -> KeyValuePair(it.key, now.toString())
                        // Optimistic force increment + calling merge on MemberInfoEntity will increment serialNumber
                        // by 2. To match this, incrementing serial number in MGM-provided context by 2 as well.
                        SERIAL -> KeyValuePair(it.key, (member.serialNumber + 2).toString())
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
                    member.isPending,
                    MEMBER_STATUS_SUSPENDED,
                    now,
                    member.memberContext,
                    serializedMgmContext,
                    member.serialNumber
                )
            )

            SuspendMemberResponse(
                PersistentMemberInfo(
                    context.holdingIdentity,
                    keyValuePairListDeserializer.deserialize(member.memberContext),
                    mgmContext,
                )
            )
        }
    }
}
