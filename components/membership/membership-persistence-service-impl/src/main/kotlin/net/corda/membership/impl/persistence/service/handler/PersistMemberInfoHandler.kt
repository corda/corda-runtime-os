package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistMemberInfo, Unit>(persistenceHandlerServices) {

    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroSerializer {
            logger.error("Failed to serialize key value pair list.")
        }
    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer (
            { logger.error("Failed to deserialize key value pair list.") },
            KeyValuePairList::class.java
        )

    private fun serializeContext(context: KeyValuePairList): ByteArray {
        return keyValuePairListSerializer.serialize(context) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }

    private fun deserialize(data: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(data) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }


    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo) {
        if (request.members.isNotEmpty()) {
            logger.info("Persisting member information.")
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                request.members.forEach {
                    val memberInfo = memberInfoFactory.create(it)
                    val currentMemberInfo = em.find(
                        MemberInfoEntity::class.java,
                        MemberInfoEntityPrimaryKey(memberInfo.groupId, memberInfo.name.toString()),
                        LockModeType.PESSIMISTIC_WRITE
                    )
                    if (currentMemberInfo?.serialNumber == memberInfo.serial) {
                        val currentMemberContext = deserialize(currentMemberInfo.memberContext)
                        val currentMgmContext = deserialize(currentMemberInfo.mgmContext)
                        if (currentMemberContext.items == it.memberContext.items) {
                            throw MembershipPersistenceException("")
                        }
                        if (currentMgmContext.items != it.mgmContext.items) {
                            throw MembershipPersistenceException("")
                        }
                        return@forEach
                    }

                    val entity = MemberInfoEntity(
                        memberInfo.groupId,
                        memberInfo.name.toString(),
                        memberInfo.status,
                        clock.instant(),
                        serializeContext(it.memberContext),
                        serializeContext(it.mgmContext),
                        memberInfo.serial
                    )
                    em.merge(entity)
                }
            }
        }
    }
}
