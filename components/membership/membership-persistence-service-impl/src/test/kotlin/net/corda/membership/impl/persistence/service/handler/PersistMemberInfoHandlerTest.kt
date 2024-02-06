package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.groupId
import net.corda.membership.lib.MemberInfoExtension.Companion.status
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class PersistMemberInfoHandlerTest {
    private companion object {
        const val SERIAL_NUMBER = 1L

        val serializedMemberContext = "123".toByteArray()
        val serializedMgmContext = "456".toByteArray()
    }

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name,
        ourGroupId
    )
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val clock = TestClock(Instant.ofEpochSecond(0))
    private val serializedContexts = ByteBuffer.wrap(byteArrayOf(1))

    private val memberProvidedContext: MemberContext = mock()
    private val signature = CryptoSignatureWithKey(
        ByteBuffer.wrap("memberSignatureKey".toByteArray()),
        ByteBuffer.wrap("memberSignatureContent".toByteArray())
    )
    private val signatureSpec = CryptoSignatureSpec("", null, null)
    private val mgmProvidedContext: MGMContext = mock()
    private val ourMemberInfo: MemberInfo = mock {
        on { memberProvidedContext } doReturn memberProvidedContext
        on { mgmProvidedContext } doReturn mgmProvidedContext
        on { groupId } doReturn ourGroupId
        on { name } doReturn ourX500Name
        on { status } doReturn MEMBER_STATUS_ACTIVE
        on { serial } doReturn SERIAL_NUMBER
    }
    private val vaultDmlConnectionId = UUID(0, 1)

    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", TestRandom.secureHash()),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0),
        timestamp = clock.instant()
    )

    private val entityTransaction: EntityTransaction = mock()
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on {
            getOrCreateEntityManagerFactory(
                eq(vaultDmlConnectionId),
                any(),
                eq(false)
            )
        } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val memberInfoFactory: MemberInfoFactory = mock {
        on { createMemberInfo(any()) } doReturn ourMemberInfo
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer<KeyValuePairList>(any(), any()) } doReturn keyValuePairListDeserializer
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(eq(ourHoldingIdentity.shortHash)) } doReturn virtualNodeInfo
    }
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()
    private val transactionTimerFactory = { _: String -> transactionTimer }
    private val services = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService,
        keyEncodingService,
        platformInfoProvider,
        mock(),
        mock(),
        mock(),
        transactionTimerFactory
    )
    private lateinit var persistMemberInfoHandler: PersistMemberInfoHandler

    @BeforeEach
    fun setUp() {
        persistMemberInfoHandler = PersistMemberInfoHandler(services)
    }

    private fun getMemberRequestContext() = MembershipRequestContext(
        clock.instant(),
        ourRegistrationId,
        ourHoldingIdentity.toAvro(),
    )

    // deprecated
    private fun getPersistMemberInfo(memberInfos: List<PersistentMemberInfo>) = PersistMemberInfo(
        null,
        memberInfos
    )

    private fun getPersistentMemberInfo() = PersistentMemberInfo(
        ourHoldingIdentity.toAvro(),
        null,
        null,
        SignedData(
            serializedContexts,
            signature,
            signatureSpec,
        ),
        serializedContexts,
    )

    @Test
    fun `invoke with no members does nothing`() {
        val result = persistMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getPersistMemberInfo(emptyList())
        )

        assertThat(result).isInstanceOf(Unit::class.java)
        verify(memberInfoFactory, never()).createMemberInfo(any())
        verify(virtualNodeInfoReadService, never()).getByHoldingIdentityShortHash(any())
        verify(dbConnectionManager, never()).getOrCreateEntityManagerFactory(any<String>(), any(), any())
        verify(jpaEntitiesRegistry, never()).get(any())
    }

    @Test
    fun `invoke with members persists`() {
        val memberInfo = getPersistentMemberInfo()
        val result = persistMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getPersistMemberInfo(listOf(memberInfo))
        )

        assertThat(result).isInstanceOf(Unit::class.java)
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(entityManagerFactory).createEntityManager()
        verify(entityManager).transaction
        verify(jpaEntitiesRegistry).get(eq(CordaDb.Vault.persistenceUnitName))
        with(argumentCaptor<PersistentMemberInfo>()) {
            verify(memberInfoFactory).createMemberInfo(capture())
            assertThat(firstValue).isEqualTo(memberInfo)
        }
        with(argumentCaptor<Any>()) {
            verify(entityManager).merge(capture())
            assertThat(firstValue).isInstanceOf(MemberInfoEntity::class.java)
            val entity = firstValue as MemberInfoEntity
            assertThat(entity.groupId).isEqualTo(ourGroupId)
            assertThat(entity.memberX500Name).isEqualTo(ourX500Name.toString())
            assertThat(entity.memberSignatureKey).isEqualTo(signature.publicKey.array())
            assertThat(entity.memberSignatureContent).isEqualTo(signature.bytes.array())
            assertThat(entity.memberSignatureSpec).isEqualTo(signatureSpec.signatureName)
        }
    }

    @Test
    fun `invoke does not persist if version already persisted`() {
        val memberInfo = getPersistentMemberInfo()
        val requestContext = getMemberRequestContext()

        mockMemberContext()
        mockMgmContext()
        mockExistingMemberInfo(requestContext.holdingIdentity)

        persistMemberInfoHandler.invoke(
            requestContext,
            PersistMemberInfo(null, listOf(memberInfo))
        )

        verify(entityManager, never()).merge(any<MemberInfoEntity>())
    }

    @Test
    fun `invoke throws if already persisted member context differs`() {
        val memberInfo = getPersistentMemberInfo()
        val requestContext = getMemberRequestContext()

        mockMemberContext(KeyValuePairList(mutableListOf(KeyValuePair("100", "b"))))
        mockMgmContext()
        mockExistingMemberInfo(requestContext.holdingIdentity)

        assertThrows<MembershipPersistenceException> {
            persistMemberInfoHandler.invoke(requestContext, PersistMemberInfo(null, listOf(memberInfo)))
        }

        verify(entityManager, never()).merge(any<MemberInfoEntity>())
    }

    @Test
    fun `invoke throws if already persisted mgm context differs`() {
        val memberInfo = getPersistentMemberInfo()
        val requestContext = getMemberRequestContext()

        mockMemberContext()
        mockMgmContext(KeyValuePairList(mutableListOf(KeyValuePair("100", "b"))))
        mockExistingMemberInfo(requestContext.holdingIdentity)

        assertThrows<MembershipPersistenceException> {
            persistMemberInfoHandler.invoke(requestContext, PersistMemberInfo(null, listOf(memberInfo)))
        }

        verify(entityManager, never()).merge(any<MemberInfoEntity>())
    }

    @Test
    fun `invoke does not persist or throw if persisted mgm context differs only by time`() {
        val mgmContextPairList = KeyValuePairList(
            mutableListOf(
                KeyValuePair(MemberInfoExtension.CREATION_TIME, "a"),
                KeyValuePair(MemberInfoExtension.MODIFIED_TIME, "b")
            )
        )
        val memberContextPairList = KeyValuePairList(emptyList())
        val mgmContextBytes = byteArrayOf(1)
        val memberContextBytes = byteArrayOf(2)
        val memberInfo = PersistentMemberInfo(
            ourHoldingIdentity.toAvro(),
            null,
            null,
            SignedData(
                ByteBuffer.wrap(memberContextBytes),
                signature,
                signatureSpec,
            ),
            ByteBuffer.wrap(mgmContextBytes)
        )
        val requestContext = getMemberRequestContext()

        mockMemberContext()
        mockMgmContext(
            KeyValuePairList(
                mutableListOf(
                    KeyValuePair(MemberInfoExtension.CREATION_TIME, "c"),
                    KeyValuePair(MemberInfoExtension.MODIFIED_TIME, "d")
                )
            )
        )
        whenever(keyValuePairListDeserializer.deserialize(eq(memberContextBytes))).doReturn(memberContextPairList)
        whenever(keyValuePairListDeserializer.deserialize(eq(mgmContextBytes))).doReturn(mgmContextPairList)
        mockExistingMemberInfo(requestContext.holdingIdentity)

        persistMemberInfoHandler.invoke(requestContext, PersistMemberInfo(null, listOf(memberInfo)))

        verify(entityManager, never()).merge(any<MemberInfoEntity>())
    }

    @Test
    fun `invoke does not throw error if updating pending member info with another pending member info with the same serial number`() {
        val serialNumber = 1L
        val memberContext = mock<MemberContext> {
            on { parse(eq(GROUP_ID), eq(String::class.java)) } doReturn ourGroupId
            on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_PENDING
        }
        val mgmContext = mock<MGMContext> {
            on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_PENDING
        }
        val newMemberInfo = mock<MemberInfo> {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
            on { it.serial } doReturn serialNumber
            on { it.name } doReturn ourX500Name
        }
        val memberInfo = getPersistentMemberInfo()
        whenever(memberInfoFactory.createMemberInfo(eq(memberInfo))).doReturn(newMemberInfo)

        val requestContext = getMemberRequestContext()

        val existingMemberInfo = mockMemberInfoEntity(
            memberStatus = MEMBER_STATUS_PENDING,
            serialNumber = serialNumber
        )
        mockExistingMemberInfo(
            requestContext.holdingIdentity,
            true,
            existingMemberInfo
        )

        mockMemberContext()
        mockMgmContext()

        persistMemberInfoHandler.invoke(
            requestContext,
            PersistMemberInfo(null, listOf(memberInfo))
        )

        verify(entityManager).merge(any<MemberInfoEntity>())
    }

    private fun mockExistingMemberInfo(
        holdingIdentity: net.corda.data.identity.HoldingIdentity,
        isPending: Boolean = false,
        existingMemberInfoEntity: MemberInfoEntity = mockMemberInfoEntity()
    ) {
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(
                    MemberInfoEntityPrimaryKey(
                        holdingIdentity.groupId,
                        holdingIdentity.x500Name,
                        isPending
                    )
                ),
                any<LockModeType>()
            )
        ).doReturn(existingMemberInfoEntity)
    }

    private fun mockMemberInfoEntity(
        memberStatus: String? = null,
        serialNumber: Long = SERIAL_NUMBER,
        memberContext: ByteArray = serializedMemberContext,
        mgmContext: ByteArray = serializedMgmContext,
    ): MemberInfoEntity {
        return mock {
            memberStatus?.let { on { this.status } doReturn it }
            on { this.serialNumber } doReturn serialNumber
            on { this.memberContext } doReturn memberContext
            on { this.mgmContext } doReturn mgmContext
        }
    }

    private fun mockMemberContext(
        deserialised: KeyValuePairList = KeyValuePairList(emptyList())
    ) {
        whenever(
            keyValuePairListDeserializer.deserialize(serializedMemberContext)
        ).doReturn(deserialised)
        whenever(
            keyValuePairListDeserializer.deserialize(serializedContexts.array())
        ).doReturn(deserialised)
    }

    private fun mockMgmContext(
        deserialised: KeyValuePairList = KeyValuePairList(emptyList())
    ) {
        whenever(
            keyValuePairListDeserializer.deserialize(serializedMgmContext)
        ).doReturn(deserialised)
        whenever(
            keyValuePairListDeserializer.deserialize(serializedContexts.array())
        ).doReturn(deserialised)
    }
}
