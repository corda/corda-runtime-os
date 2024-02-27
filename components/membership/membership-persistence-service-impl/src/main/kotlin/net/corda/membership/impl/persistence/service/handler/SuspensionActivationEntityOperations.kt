package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.utilities.serialization.wrapWithNullErrorHandling
import net.corda.utilities.time.Clock
import javax.persistence.EntityManager
import javax.persistence.LockModeType
import javax.persistence.PessimisticLockException

internal class SuspensionActivationEntityOperations(
    private val clock: Clock,
    private val keyValuePairListSerializer: CordaAvroSerializer<KeyValuePairList>,
    private val memberInfoFactory: MemberInfoFactory,
) {
    @Suppress("ThrowsCount")
    fun findMember(
        em: EntityManager,
        memberName: String,
        groupId: String,
        expectedSerial: Long?,
        expectedStatus: String,
    ): MemberInfoEntity {
        val member = try {
            em.find(
                MemberInfoEntity::class.java,
                MemberInfoEntityPrimaryKey(groupId, memberName, false),
                LockModeType.PESSIMISTIC_WRITE,
            ) ?: throw MembershipPersistenceException("Member '$memberName' does not exist.")
        } catch (e: PessimisticLockException) {
            throw InvalidEntityUpdateException(
                "Could not update member '$memberName' with serial number: $expectedSerial: ${e.message}",
            )
        }
        expectedSerial?.let {
            if (member.serialNumber != it) {
                throw InvalidEntityUpdateException(
                    "The provided serial number '$expectedSerial' does not match the current version '${member.serialNumber}' of " +
                        "MemberInfo for member '$memberName'."
                )
            }
        }
        if (member.status != expectedStatus) {
            throw InvalidEntityUpdateException(
                "This action cannot be performed on member '$memberName' because it has status '${member.status}'."
            )
        }
        return member
    }

    @Suppress("LongParameterList")
    fun updateStatus(
        em: EntityManager,
        memberName: String,
        mgmHoldingIdentity: HoldingIdentity,
        currentMemberInfo: MemberInfoEntity,
        currentMgmContext: KeyValuePairList,
        newStatus: String
    ): PersistentMemberInfo {
        val now = clock.instant()
        val updatedSerial = currentMemberInfo.serialNumber + 1
        val mgmContext = KeyValuePairList(
            currentMgmContext.items.map {
                when (it.key) {
                    STATUS -> KeyValuePair(it.key, newStatus)
                    MODIFIED_TIME -> KeyValuePair(it.key, now.toString())
                    SERIAL -> KeyValuePair(it.key, updatedSerial.toString())
                    else -> it
                }
            }
        )
        val serializedMgmContext = wrapWithNullErrorHandling({
            MembershipPersistenceException("Failed to serialize the MGM-provided context.", it)
        }) {
            keyValuePairListSerializer.serialize(mgmContext)
        }

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
                updatedSerial,
                isDeleted = false
            ),
        )

        return memberInfoFactory.createPersistentMemberInfo(
            mgmHoldingIdentity,
            currentMemberInfo.memberContext,
            serializedMgmContext,
            currentMemberInfo.memberSignatureKey,
            currentMemberInfo.memberSignatureContent,
            currentMemberInfo.memberSignatureSpec,
        )
    }
}
