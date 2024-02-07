package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.SuspendMember
import net.corda.data.membership.db.response.command.SuspendMemberResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.notaryDetails
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.toSortedMap
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class SuspendMemberHandlerTest {

    private companion object {
        const val SERIAL_NUMBER = 5L
        const val REASON = "test"
        const val EPOCH = 6
        const val KNOWN_NOTARY_SERVICE = "O=NotaryA, L=LDN, C=GB"
        const val KNOWN_NOTARY_PROTOCOL = "net.corda.notary.MyNotaryService"
        const val NOTARY_SERVICE_NUMBER = 5
    }

    private val knownGroupId = UUID(0, 1).toString()
    private val knownX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val holdingIdentity = HoldingIdentity(knownX500Name, knownGroupId)
    private val ourVirtualNodeInfo: VirtualNodeInfo = mock {
        on { vaultDmlConnectionId } doReturn UUID(0, 1)
    }
    private val clock: Clock = TestClock(Instant.ofEpochSecond(1))

    private val transaction: EntityTransaction = mock()
    private val resultList: List<GroupParametersEntity> = listOf(mock())
    private val previousGroupParameters = "test".toByteArray()
    private val previousEntry: TypedQuery<GroupParametersEntity> = mock {
        on { resultList } doReturn resultList
        on { singleResult } doReturn GroupParametersEntity(
            epoch = EPOCH,
            parameters = previousGroupParameters,
            signaturePublicKey = byteArrayOf(0),
            signatureContent = byteArrayOf(1),
            signatureSpec = SignatureSpecs.ECDSA_SHA256.signatureName
        )
    }
    private val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        on { setMaxResults(1) } doReturn previousEntry
    }
    private val root = mock<Root<GroupParametersEntity>> {
        on { get<String>("epoch") } doReturn mock<Path<String>>()
    }
    private val order = mock<Order>()
    private val query = mock<CriteriaQuery<GroupParametersEntity>> {
        on { from(GroupParametersEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val status = mock<Path<String>>()
    private val memberX500Name = mock<Path<String>>()
    private val notEqualPredicate = mock<Predicate>()
    private val equalPredicate = mock<Predicate>()
    private val membersQuery: TypedQuery<MemberInfoEntity> = mock {
        on { setLockMode(LockModeType.PESSIMISTIC_WRITE) } doReturn mock
        on { resultList } doReturn emptyList()
    }
    private val memberRoot = mock<Root<MemberInfoEntity>> {
        on { get<String>("status") } doReturn status
        on { get<String>("memberX500Name") } doReturn memberX500Name
    }
    private val memberCriteriaQuery = mock<CriteriaQuery<MemberInfoEntity>> {
        on { from(MemberInfoEntity::class.java) } doReturn memberRoot
        on { select(memberRoot) } doReturn mock
        on { where(equalPredicate, notEqualPredicate) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(GroupParametersEntity::class.java) } doReturn query
        on { createQuery(MemberInfoEntity::class.java) } doReturn memberCriteriaQuery
        on { desc(any()) } doReturn order
        on { equal(status, MEMBER_STATUS_ACTIVE) } doReturn equalPredicate
        on { notEqual(memberX500Name, knownX500Name.toString()) } doReturn notEqualPredicate
    }
    private val em: EntityManager = mock {
        on { persist(any<GroupParametersEntity>()) } doAnswer {}
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(query)) } doReturn groupParametersQuery
        on { createQuery(eq(memberCriteriaQuery)) } doReturn membersQuery
        on { transaction } doReturn transaction
    }
    private val emf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em
    }
    private val dbConnectionManager: DbConnectionManager = mock {
        on { getOrCreateEntityManagerFactory(any<UUID>(), any(), eq(false)) } doReturn emf
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(holdingIdentity.shortHash) } doReturn ourVirtualNodeInfo
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(any()) } doReturn mock()
    }
    private val serializedMgmContext = "1234".toByteArray()
    private val memberInfoEntity = mock<MemberInfoEntity> {
        on { mgmContext } doReturn serializedMgmContext
    }
    private val mgmContext = KeyValuePairList(
        listOf(
            KeyValuePair("one", "1")
        )
    )
    private val persistentMemberInfo = mock<PersistentMemberInfo>()
    private val suspensionActivationEntityOperations =
        mock<SuspensionActivationEntityOperations> {
            on {
                mock.findMember(
                    em,
                    holdingIdentity.x500Name.toString(),
                    holdingIdentity.groupId,
                    SERIAL_NUMBER,
                    MEMBER_STATUS_ACTIVE
                )
            } doReturn(memberInfoEntity)
            on {
                mock.updateStatus(
                    em,
                    holdingIdentity.x500Name.toString(),
                    holdingIdentity.toAvro(),
                    memberInfoEntity,
                    mgmContext,
                    MEMBER_STATUS_SUSPENDED
                )
            }.doReturn(persistentMemberInfo)
        }
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn byteArrayOf(0)
    }
    private val groupParametersWithNotary = KeyValuePairList(
        listOf(
            KeyValuePair(GroupParametersNotaryUpdater.EPOCH_KEY, EPOCH.toString()),
            KeyValuePair(String.format(GroupParametersNotaryUpdater.NOTARY_SERVICE_NAME_KEY, NOTARY_SERVICE_NUMBER), KNOWN_NOTARY_SERVICE),
            KeyValuePair(
                String.format(GroupParametersNotaryUpdater.NOTARY_SERVICE_PROTOCOL_KEY, NOTARY_SERVICE_NUMBER),
                KNOWN_NOTARY_PROTOCOL
            ),
            KeyValuePair(String.format(GroupParametersNotaryUpdater.NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, NOTARY_SERVICE_NUMBER, 0), "1"),
            KeyValuePair(
                String.format(GroupParametersNotaryUpdater.NOTARY_SERVICE_KEYS_KEY, NOTARY_SERVICE_NUMBER, 0),
                "existing-test-key"
            ),
        )
    )
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serializedMgmContext) } doReturn mgmContext
        on { deserialize(previousGroupParameters) } doReturn groupParametersWithNotary
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on {
            createAvroDeserializer(
                any(),
                eq(KeyValuePairList::class.java)
            )
        } doReturn keyValuePairListDeserializer
        on {
            createAvroSerializer<KeyValuePairList>(any())
        } doReturn keyValuePairListSerializer
    }
    private val memberInfo = mock<MemberInfo> {
        on { memberProvidedContext } doReturn mock()
    }
    private val memberInfoFactory = mock<MemberInfoFactory> {
        on { createMemberInfo(persistentMemberInfo) } doReturn memberInfo
    }
    private val persistenceHandlerServices: PersistenceHandlerServices = mock {
        on { clock } doReturn clock
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn cordaAvroSerializationFactory
        on { memberInfoFactory } doReturn memberInfoFactory
        on { keyEncodingService } doReturn mock()
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val notaryUpdater = mock<GroupParametersNotaryUpdater>()
    private val handler: SuspendMemberHandler = SuspendMemberHandler(persistenceHandlerServices, notaryUpdater) { _, _ ->
        suspensionActivationEntityOperations
    }
    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        holdingIdentity.toAvro()
    )
    private val request = SuspendMember(knownX500Name.toString(), SERIAL_NUMBER, REASON)

    private fun invokeTestFunction(): SuspendMemberResponse {
        return handler.invoke(context, request)
    }

    @Test
    fun `invoke returns the correct data when member is not a notary`() {
        val result = invokeTestFunction()

        assertThat(result.memberInfo).isEqualTo(persistentMemberInfo)
        assertThat(result.groupParameters).isNull()
    }

    @Test
    fun `invoke returns the correct data when member is a notary`() {
        val mockMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE,
            ).entries
        }
        val memberNotaryDetails = mock<MemberNotaryDetails> {
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { serviceProtocol } doReturn "Protocol"
            on { serviceProtocolVersions } doReturn listOf(1, 2, 3)
        }
        val mockMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
            on { notaryDetails } doReturn memberNotaryDetails
            on { name } doReturn knownX500Name
        }
        whenever(memberInfoFactory.createMemberInfo(persistentMemberInfo)).thenReturn(mockMemberInfo)
        val updatedSerializedGroupParameters = mock<KeyValuePairList>()
        val serializedGroupParameters = "101112".toByteArray()
        whenever(keyValuePairListSerializer.serialize(updatedSerializedGroupParameters)).doReturn(serializedGroupParameters)
        whenever(notaryUpdater.removeNotaryService(any(), any()))
            .doReturn(10 to updatedSerializedGroupParameters)

        val result = invokeTestFunction()

        assertThat(result.memberInfo).isEqualTo(persistentMemberInfo)
        with(result.groupParameters) {
            assertThat(this.groupParameters).isEqualTo(ByteBuffer.wrap(serializedGroupParameters))
            assertThat(this.mgmSignature).isNull()
            assertThat(this.mgmSignatureSpec).isNull()
        }
    }

    @Test
    fun `invoke updates the group parameters when member is a notary`() {
        val newEpoch = 10
        val mockMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE,
            ).entries
        }
        val memberNotaryDetails = mock<MemberNotaryDetails> {
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { serviceProtocol } doReturn "Protocol"
            on { serviceProtocolVersions } doReturn listOf(1, 2, 3)
        }
        val mockMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
            on { notaryDetails } doReturn memberNotaryDetails
            on { name } doReturn knownX500Name
        }
        whenever(memberInfoFactory.createMemberInfo(persistentMemberInfo)).thenReturn(mockMemberInfo)
        val updatedSerializedGroupParameters = mock<KeyValuePairList>()
        val serializedGroupParameters = "101112".toByteArray()
        whenever(keyValuePairListSerializer.serialize(updatedSerializedGroupParameters)).doReturn(serializedGroupParameters)
        whenever(notaryUpdater.removeNotaryService(any(), any()))
            .doReturn(newEpoch to updatedSerializedGroupParameters)

        invokeTestFunction()
        verify(em).persist(GroupParametersEntity(newEpoch, serializedGroupParameters, null, null, null))
    }

    @Test
    fun `invoke returns the correct data when member is a member of a notary service`() {
        val mockMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE,
            ).entries
        }
        val memberNotaryDetails = mock<MemberNotaryDetails> {
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { serviceProtocol } doReturn "Protocol"
            on { serviceProtocolVersions } doReturn listOf(1, 2, 3)
        }
        val mockMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
            on { notaryDetails } doReturn memberNotaryDetails
            on { name } doReturn knownX500Name
        }
        whenever(memberInfoFactory.createMemberInfo(persistentMemberInfo)).thenReturn(mockMemberInfo)

        val otherNotaryMemberContext = "OtherNotaryMemberContext".toByteArray()
        val otherNotaryMgmContext = "OtherNotaryMgmContext".toByteArray()
        val otherMemberOfNotaryService = mock<MemberInfoEntity> {
            on { memberContext } doReturn otherNotaryMemberContext
            on { mgmContext } doReturn otherNotaryMgmContext
        }
        val deserializedOtherNotaryMemberContext = mock<KeyValuePairList>()
        val deserializedOtherNotaryMgmContext = mock<KeyValuePairList>()
        whenever(keyValuePairListDeserializer.deserialize(otherNotaryMemberContext)).doReturn(deserializedOtherNotaryMemberContext)
        whenever(keyValuePairListDeserializer.deserialize(otherNotaryMgmContext)).doReturn(deserializedOtherNotaryMgmContext)
        whenever(membersQuery.resultStream).doAnswer { listOf(otherMemberOfNotaryService).stream() }
        whenever(
            memberInfoFactory.createMemberInfo(
                deserializedOtherNotaryMemberContext.toSortedMap(),
                deserializedOtherNotaryMgmContext.toSortedMap()
            )
        ).thenReturn(mockMemberInfo)

        val updatedSerializedGroupParameters = mock<KeyValuePairList>()
        val serializedGroupParameters = "101112".toByteArray()
        whenever(keyValuePairListSerializer.serialize(updatedSerializedGroupParameters)).doReturn(serializedGroupParameters)
        whenever(
            notaryUpdater
                .removeNotaryFromExistingNotaryService(any(), any(), eq(NOTARY_SERVICE_NUMBER), eq(listOf(memberNotaryDetails)))
        )
            .doReturn(10 to updatedSerializedGroupParameters)

        val result = invokeTestFunction()

        assertThat(result.memberInfo).isEqualTo(persistentMemberInfo)
        with(result.groupParameters) {
            assertThat(this.groupParameters).isEqualTo(ByteBuffer.wrap(serializedGroupParameters))
            assertThat(this.mgmSignature).isNull()
            assertThat(this.mgmSignatureSpec).isNull()
        }
    }

    @Test
    fun `invoke updates the group parameters when member is a member of a notary service`() {
        val newEpoch = 10
        val mockMemberContext = mock<MemberContext> {
            on { entries } doReturn mapOf(
                "${MemberInfoExtension.ROLES_PREFIX}.0" to MemberInfoExtension.NOTARY_ROLE,
            ).entries
        }
        val memberNotaryDetails = mock<MemberNotaryDetails> {
            on { serviceName } doReturn MemberX500Name.parse(KNOWN_NOTARY_SERVICE)
            on { serviceProtocol } doReturn "Protocol"
            on { serviceProtocolVersions } doReturn listOf(1, 2, 3)
        }
        val mockMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn mockMemberContext
            on { notaryDetails } doReturn memberNotaryDetails
            on { name } doReturn knownX500Name
        }
        whenever(memberInfoFactory.createMemberInfo(persistentMemberInfo)).thenReturn(mockMemberInfo)

        val otherNotaryMemberContext = "OtherNotaryMemberContext".toByteArray()
        val otherNotaryMgmContext = "OtherNotaryMgmContext".toByteArray()
        val otherMemberOfNotaryService = mock<MemberInfoEntity> {
            on { memberContext } doReturn otherNotaryMemberContext
            on { mgmContext } doReturn otherNotaryMgmContext
        }
        val deserializedOtherNotaryMemberContext = mock<KeyValuePairList>()
        val deserializedOtherNotaryMgmContext = mock<KeyValuePairList>()
        whenever(keyValuePairListDeserializer.deserialize(otherNotaryMemberContext)).doReturn(deserializedOtherNotaryMemberContext)
        whenever(keyValuePairListDeserializer.deserialize(otherNotaryMgmContext)).doReturn(deserializedOtherNotaryMgmContext)
        whenever(membersQuery.resultStream).doAnswer { listOf(otherMemberOfNotaryService).stream() }
        whenever(
            memberInfoFactory.createMemberInfo(
                deserializedOtherNotaryMemberContext.toSortedMap(),
                deserializedOtherNotaryMgmContext.toSortedMap()
            )
        ).thenReturn(mockMemberInfo)

        val updatedSerializedGroupParameters = mock<KeyValuePairList>()
        val serializedGroupParameters = "101112".toByteArray()
        whenever(keyValuePairListSerializer.serialize(updatedSerializedGroupParameters)).doReturn(serializedGroupParameters)
        whenever(
            notaryUpdater
                .removeNotaryFromExistingNotaryService(any(), any(), eq(NOTARY_SERVICE_NUMBER), eq(listOf(memberNotaryDetails)))
        )
            .doReturn(newEpoch to updatedSerializedGroupParameters)

        invokeTestFunction()

        verify(em).persist(GroupParametersEntity(newEpoch, serializedGroupParameters, null, null, null))
    }

    @Test
    fun `invoke throws a MembershipPersistenceException if the mgmContext can't be deserialized`() {
        whenever(keyValuePairListDeserializer.deserialize(serializedMgmContext)).thenReturn(null)
        assertThrows<MembershipPersistenceException> { invokeTestFunction() }.also {
            assertThat(it).hasMessageContaining("Failed to deserialize")
        }
    }
}
