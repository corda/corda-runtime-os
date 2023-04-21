package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity

class SuspensionActivationEntityOperationsTest {

    private companion object {
        const val SERIAL_NUMBER = 5L
        const val SIGNATURE_SPEC = ""
    }

    private val knownGroupId = UUID(0, 1).toString()
    private val knownX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val primaryKey = MemberInfoEntityPrimaryKey(
        groupId = knownGroupId,
        memberX500Name = knownX500Name.toString(),
        false
    )
    private val contextBytes = byteArrayOf(1, 10)
    private val signatureKey = byteArrayOf(5)
    private val signatureContent = byteArrayOf(6)
    private val holdingIdentity = HoldingIdentity(knownX500Name, knownGroupId)
    private val clock: Clock = TestClock(Instant.ofEpochSecond(1))

    private val transaction: EntityTransaction = mock()
    private val em: EntityManager = mock {
        on { transaction } doReturn transaction
    }
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(0)
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()

    private val handler = SuspensionActivationEntityOperations(clock, keyValuePairListDeserializer, keyValuePairListSerializer)
    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        holdingIdentity.toAvro()
    )

    private fun invokeTestFunctionWithError(
        testFunction: () -> PersistentMemberInfo,
        errorMsg: String,
        type: Class<*> = MembershipPersistenceException::class.java
    ) {
        assertThrows<Exception> {
            testFunction.invoke()
        }.apply {
            assertThat(this).isInstanceOf(type)
            assertThat(message).contains(errorMsg)
        }
    }

    @Suppress("LongParameterList")
    private fun mockMemberInfoEntity(
        mgmProvidedContext: ByteArray = contextBytes,
        memberProvidedContext: ByteArray = contextBytes,
        group: String? = knownGroupId,
        name: MemberX500Name? = knownX500Name,
        serial: Long? = SERIAL_NUMBER,
        memberStatus: String = MEMBER_STATUS_SUSPENDED
    ) {
        val mockEntity = mock<MemberInfoEntity> {
            on { mgmContext } doReturn mgmProvidedContext
            on { memberContext } doReturn memberProvidedContext
            on { groupId } doReturn group!!
            on { memberX500Name } doReturn name.toString()
            on { serialNumber } doReturn serial!!
            on { status } doReturn memberStatus
            on { memberSignatureKey } doReturn signatureKey
            on { memberSignatureContent } doReturn signatureContent
            on { memberSignatureSpec} doReturn SIGNATURE_SPEC
        }
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(mockEntity)
    }

    @Test
    fun `findMember finds the correct entity`() {
        val currentStatus = "Status"
        val mockEntity = mock<MemberInfoEntity> {
            on { status } doReturn currentStatus
            on { serialNumber } doReturn SERIAL_NUMBER
        }
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(mockEntity)

        val result = handler.findMember(em, knownX500Name.toString(), knownGroupId, SERIAL_NUMBER, currentStatus)

        assertThat(result).isEqualTo(mockEntity)
    }

    @Test
    fun `findMember throws an MembershipPersistenceException if the member cannot be found`() {
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(null)

        assertThrows<MembershipPersistenceException> {
            handler.findMember(em, knownX500Name.toString(), knownGroupId, null, "")
        }.apply {
            assertThat(this.message).contains("does not exist")
        }
    }

    @Test
    fun `findMember throws an InvalidEntityUpdateException if the serial is different from expected`() {
        val currentStatus = "Status"
        val mockEntity = mock<MemberInfoEntity> {
            on { status } doReturn currentStatus
            on { serialNumber } doReturn SERIAL_NUMBER
        }
        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(mockEntity)

        assertThrows<InvalidEntityUpdateException> {
            handler.findMember(em, knownX500Name.toString(), knownGroupId, SERIAL_NUMBER + 10, currentStatus)
        }.apply {
            assertThat(this.message).contains("does not match the current version")
        }
    }

    @Test
    fun `findMember throws an IllegalArgumentException if status is different from expected`() {
        val currentStatus = "Status"
        val expectedStatus = "expected"
        val mockEntity = mock<MemberInfoEntity> {
            on { status } doReturn currentStatus
        }

        whenever(
            em.find(eq(MemberInfoEntity::class.java), eq(primaryKey), eq(LockModeType.PESSIMISTIC_WRITE))
        ).doReturn(mockEntity)

        assertThrows<IllegalArgumentException> {
            handler.findMember(em, knownX500Name.toString(), knownGroupId, null, expectedStatus)
        }.apply {
            assertThat(this.message).contains("cannot be performed")
        }
    }

    @Test
    fun `updateStatus persists the correct entity and returns the expected PersistentMemberInfo`() {
        val mockEntity = mock<MemberInfoEntity> {
            on { mgmContext } doReturn contextBytes
            on { memberContext } doReturn contextBytes
            on { groupId } doReturn knownGroupId
            on { memberX500Name } doReturn knownX500Name.toString()
            on { serialNumber } doReturn SERIAL_NUMBER
            on { status } doReturn "status"
            on { memberSignatureKey } doReturn signatureKey
            on { memberSignatureContent } doReturn signatureContent
            on { memberSignatureSpec} doReturn SIGNATURE_SPEC
        }
        val memberContext = KeyValuePairList(
            listOf(
                KeyValuePair("key", "value"),
            )
        )
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(memberContext)
        mockMemberInfoEntity()
        val entityCapture = argumentCaptor<MemberInfoEntity>()
        val mgmContext = KeyValuePairList(
            listOf(
                KeyValuePair(STATUS, "status"),
                KeyValuePair(MODIFIED_TIME, "currentTime"),
                KeyValuePair(SERIAL, "serial"),
                KeyValuePair("KEY", "value")
            )
        )
        val serializedMGMContext = byteArrayOf(0)
        whenever(keyValuePairListSerializer.serialize(mgmContext)).doReturn(serializedMGMContext)
        whenever(em.merge(entityCapture.capture())).doReturn(mock())
        val mgmHoldingIdentity = AvroHoldingIdentity("MGM", knownGroupId)
        val newStatus = "newStatus"

        val persistentMemberInfo = handler.updateStatus(
            em,
            knownX500Name.toString(),
            mgmHoldingIdentity,
            mockEntity,
            mgmContext,
            newStatus
        )

        with(entityCapture.firstValue) {
            assertThat(status).isEqualTo(newStatus)
            assertThat(serialNumber).isEqualTo(SERIAL_NUMBER + 1)
            assertThat(modifiedTime).isEqualTo(clock.instant())
            assertThat(groupId).isEqualTo(knownGroupId)
            assertThat(memberX500Name).isEqualTo(knownX500Name.toString())
            assertThat(this.mgmContext).isEqualTo(serializedMGMContext)
            assertThat(isPending).isFalse
            assertThat(memberSignatureKey).isEqualTo(signatureKey)
            assertThat(memberSignatureContent).isEqualTo(signatureContent)
            assertThat(memberSignatureSpec).isEqualTo(SIGNATURE_SPEC)
        }
        assertThat(persistentMemberInfo.viewOwningMember).isEqualTo(mgmHoldingIdentity)
        assertThat(persistentMemberInfo.memberContext).isEqualTo(memberContext)
        assertThat(persistentMemberInfo.mgmContext.items).containsExactlyInAnyOrder(
            KeyValuePair(STATUS, newStatus),
            KeyValuePair(MODIFIED_TIME, clock.instant().toString()),
            KeyValuePair(SERIAL, (SERIAL_NUMBER + 1).toString()),
            KeyValuePair("KEY", "value")
        )
    }

    @Test
    fun `updateStatus throws exception if MGM-provided context cannot be serialized`() {
        whenever(keyValuePairListSerializer.serialize(any())).doReturn(null)
        assertThrows<MembershipPersistenceException> {
            handler.updateStatus(em, knownX500Name.toString(), mock(), mock(), mock(), "")
        }.apply {
            assertThat(this.message).contains("Failed to serialize")
        }
    }

    @Test
    fun `updateStatus throws exception if MGM-provided context cannot be deserialized`() {
        val mockEntity = mock<MemberInfoEntity> {
            on { mgmContext } doReturn contextBytes
            on { memberContext } doReturn contextBytes
            on { groupId } doReturn knownGroupId
            on { memberX500Name } doReturn knownX500Name.toString()
            on { serialNumber } doReturn SERIAL_NUMBER
            on { status } doReturn "status"
            on { memberSignatureKey } doReturn signatureKey
            on { memberSignatureContent } doReturn signatureContent
            on { memberSignatureSpec} doReturn SIGNATURE_SPEC
        }
        whenever(keyValuePairListDeserializer.deserialize(contextBytes)).doReturn(null)

        assertThrows<MembershipPersistenceException> {
            handler.updateStatus(em, knownX500Name.toString(), mock { on {groupId} doReturn knownGroupId }, mockEntity, mock(), "")
        }.apply {
            assertThat(this.message).contains("Failed to deserialize")
        }
    }
}