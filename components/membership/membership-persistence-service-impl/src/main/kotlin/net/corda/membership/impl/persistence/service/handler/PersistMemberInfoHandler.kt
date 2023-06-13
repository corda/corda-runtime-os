package net.corda.membership.impl.persistence.service.handler

import javax.persistence.LockModeType
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda

internal class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistMemberInfo, Unit>(persistenceHandlerServices) {

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            { logger.error("Failed to deserialize key value pair list.") },
            KeyValuePairList::class.java
        )

    private fun deserialize(data: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(data) ?: throw MembershipPersistenceException(
            "Failed to serialize key value pair list."
        )
    }


    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo) {
        if (request.signedMembers.isNotEmpty()) {
            logger.info("Persisting member information.")
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                request.signedMembers.forEach {
                    val memberInfo = memberInfoFactory.createMemberInfo(it)
                    val currentMemberInfo = em.find(
                        MemberInfoEntity::class.java,
                        MemberInfoEntityPrimaryKey(
                            memberInfo.groupId,
                            memberInfo.name.toString(),
                            memberInfo.status == MEMBER_STATUS_PENDING
                        ),
                        LockModeType.PESSIMISTIC_WRITE
                    )
                    if (currentMemberInfo?.serialNumber == memberInfo.serial) {
                        val currentMemberContext = deserialize(currentMemberInfo.memberContext)
                        val currentMgmContext = deserialize(currentMemberInfo.mgmContext)
                        val updatedMemberContext = deserialize(it.signedMemberContext.data.array())
                        val updatedMGMContext = deserialize(it.serializedMgmContext.array())
                        if (currentMemberContext.items != updatedMemberContext.items) {
                            throw MembershipPersistenceException("Cannot update member info with same serial number " +
                                "(${memberInfo.serial}): member context differs from original.")
                        }
                        if (currentMgmContext.toMap().removeTime() != updatedMGMContext.toMap().removeTime()) {
                            throw MembershipPersistenceException(
                                "Cannot update member info with same serial number " +
                                        "(${memberInfo.serial}): mgm context differs from original."
                            )
                        }
                        return@forEach
                    }

                    val entity = MemberInfoEntity(
                        memberInfo.groupId,
                        memberInfo.name.toString(),
                        memberInfo.status == MEMBER_STATUS_PENDING,
                        memberInfo.status,
                        clock.instant(),
                        it.signedMemberContext.data.array(),
                        it.signedMemberContext.signature.publicKey.array(),
                        it.signedMemberContext.signature.bytes.array(),
                        it.signedMemberContext.signatureSpec.signatureName,
                        it.serializedMgmContext.array(),
                        memberInfo.serial,
                    )
                    em.merge(entity)
                }
            }
        }
    }

    private fun Map<String, String>.removeTime(): Map<String, String> = this.filterKeys {
        it != MemberInfoExtension.CREATION_TIME && it != MemberInfoExtension.MODIFIED_TIME
    }
}
