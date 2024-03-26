package net.corda.membership.impl.persistence.service.handler

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
import net.corda.membership.lib.exceptions.ConflictPersistenceException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.toMap
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<PersistMemberInfo, Unit>(persistenceHandlerServices) {
    override val operation = PersistMemberInfo::class.java

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            { logger.error("Failed to deserialize key value pair list.") },
            KeyValuePairList::class.java
        )

    private fun deserialize(data: ByteArray): KeyValuePairList {
        return keyValuePairListDeserializer.deserialize(data) ?: throw MembershipPersistenceException(
            "Failed to deserialize key value pair list."
        )
    }

    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo) {
        if (request.signedMembers.isNotEmpty()) {
            logger.info("Persisting member information.")
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                request.signedMembers.forEach {
                    val newMemberInfo = memberInfoFactory.createMemberInfo(it)
                    logger.info(
                        "Persisting member information representing ${newMemberInfo.name} as viewed " +
                            "by ${context.holdingIdentity.x500Name} in group ${context.holdingIdentity.groupId}."
                    )
                    val oldMemberInfo = em.find(
                        MemberInfoEntity::class.java,
                        MemberInfoEntityPrimaryKey(
                            newMemberInfo.groupId,
                            newMemberInfo.name.toString(),
                            newMemberInfo.status == MEMBER_STATUS_PENDING
                        ),
                        LockModeType.PESSIMISTIC_WRITE
                    )
                    val newPendingVersion = newMemberInfo.status == MEMBER_STATUS_PENDING &&
                        oldMemberInfo?.status == MEMBER_STATUS_PENDING
                    if (!newPendingVersion && oldMemberInfo?.serialNumber == newMemberInfo.serial) {
                        val currentMemberContext = deserialize(oldMemberInfo.memberContext)
                        val currentMgmContext = deserialize(oldMemberInfo.mgmContext)
                        val updatedMemberContext = deserialize(it.signedMemberContext.data.array())
                        val updatedMGMContext = deserialize(it.serializedMgmContext.array())
                        if (currentMemberContext.items != updatedMemberContext.items) {
                            throw ConflictPersistenceException(
                                "Cannot update member info with same serial number " +
                                    "(${newMemberInfo.serial}): member context differs from original."
                            )
                        }
                        if (currentMgmContext.toMap().removeTime() != updatedMGMContext.toMap()
                                .removeTime()
                        ) {
                            throw ConflictPersistenceException(
                                "Cannot update member info with same serial number " +
                                    "(${newMemberInfo.serial}): mgm context differs from original."
                            )
                        }
                        return@forEach
                    }

                    val entity = MemberInfoEntity(
                        newMemberInfo.groupId,
                        newMemberInfo.name.toString(),
                        newMemberInfo.status == MEMBER_STATUS_PENDING,
                        newMemberInfo.status,
                        clock.instant(),
                        it.signedMemberContext.data.array(),
                        it.signedMemberContext.signature.publicKey.array(),
                        it.signedMemberContext.signature.bytes.array(),
                        it.signedMemberContext.signatureSpec.signatureName,
                        it.serializedMgmContext.array(),
                        newMemberInfo.serial,
                        isDeleted = false
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
