package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ActivateMember
import net.corda.data.membership.db.response.command.ActivateMemberResponse
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

internal class ActivateMemberHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<ActivateMember, ActivateMemberResponse>(persistenceHandlerServices) {

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

    override fun invoke(context: MembershipRequestContext, request: ActivateMember): ActivateMemberResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            val now = clock.instant()
            val activatedMember = request.activatedMember
            val member = em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(context.holdingIdentity.groupId, activatedMember, false),
                LockModeType.OPTIMISTIC_FORCE_INCREMENT
            ) ?: throw MembershipPersistenceException("Member '$activatedMember' does not exist.")
            request.serialNumber?.let {
                require(member.serialNumber == it) {
                    throw MembershipPersistenceException("The provided serial number does not match to the current " +
                            "version of MemberInfo for member '$activatedMember'.")
                }
            }
            require(member.status == MEMBER_STATUS_SUSPENDED) {
                throw MembershipPersistenceException("Member '$activatedMember' cannot be activated because" +
                        " it has status '${member.status}'.")
            }
            val currentMgmContext = keyValuePairListDeserializer.deserialize(member.mgmContext)
                ?: throw MembershipPersistenceException("Failed to extract the MGM-provided context.")
            val mgmContext = KeyValuePairList(
                currentMgmContext.items.map {
                    when (it.key) {
                        STATUS -> KeyValuePair(it.key, MEMBER_STATUS_ACTIVE)
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
                    MEMBER_STATUS_ACTIVE,
                    now,
                    member.memberContext,
                    serializedMgmContext,
                    request.serialNumber
                )
            )

            ActivateMemberResponse(
                PersistentMemberInfo(
                    context.holdingIdentity,
                    keyValuePairListDeserializer.deserialize(member.memberContext),
                    mgmContext,
                )
            )
        }
    }
}
