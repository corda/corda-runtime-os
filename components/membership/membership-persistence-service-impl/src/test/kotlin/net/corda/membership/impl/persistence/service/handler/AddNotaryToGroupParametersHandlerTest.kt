package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_ROLE
import net.corda.membership.lib.MemberInfoExtension.Companion.NOTARY_SERVICE_PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.ROLES_PREFIX
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.membership.lib.toWire
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import kotlin.test.assertFailsWith

class AddNotaryToGroupParametersHandlerTest {
    private companion object {
        const val EPOCH_KEY = "corda.epoch"
        const val NOTARY_PLUGIN_KEY = "corda.notary.service.plugin"
        const val EPOCH = 1
        const val KNOWN_NOTARY_SERVICE = "O=NotaryA, L=LDN, C=GB"
        const val KNOWN_NOTARY_PLUGIN = "net.corda.notary.MyNotaryService"
    }
    private val knownIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
    private val context = byteArrayOf(1, 2, 3)
    private val serializeCaptor = argumentCaptor<KeyValuePairList>()
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(serializeCaptor.capture()) } doReturn context
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val identity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group").toCorda()
    private val vaultDmlConnectionId = UUID(1, 2)
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn identity
        on { vaultDmlConnectionId } doReturn vaultDmlConnectionId
    }
    private val nodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(any()) } doReturn nodeInfo
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val registry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val transaction = mock<EntityTransaction>()
    private val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
        on { resultList } doReturn listOf(
            GroupParametersEntity(EPOCH, "test".toByteArray())
        )
    }
    private val entityManager = mock<EntityManager> {
        on { persist(any<GroupParametersEntity>()) } doAnswer {}
        on { createQuery(any(), eq(GroupParametersEntity::class.java)) } doReturn groupParametersQuery
        on { transaction } doReturn transaction
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val connectionManager = mock<DbConnectionManager> {
        on {
            createEntityManagerFactory(
                vaultDmlConnectionId,
                entitySet
            )
        } doReturn entityManagerFactory
    }
    private val clock = TestClock(Instant.ofEpochMilli(10))
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsString(any()) } doReturn "test-key"
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { clock } doReturn clock
        on { keyEncodingService } doReturn keyEncodingService
    }
    private val handler = AddNotaryToGroupParametersHandler(persistenceHandlerServices)

    @Test
    fun `invoke with new notary service name adds new notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
        }
        val memberContext: MemberContext = mock {
            on { get(eq(NOTARY_SERVICE_PARTY_NAME)) } doReturn KNOWN_NOTARY_SERVICE
            on { get(eq(NOTARY_PLUGIN_KEY)) } doReturn KNOWN_NOTARY_PLUGIN
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(KeyValuePair(EPOCH_KEY, EPOCH.toString())))
        )

        val result = handler.invoke(requestContext, request)
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))
        with(argumentCaptor<Any>()) {
            verify(entityManager).persist(capture())
            assertThat(firstValue).isInstanceOf(GroupParametersEntity::class.java)
            val entity = firstValue as GroupParametersEntity
            assertThat(entity.epoch).isEqualTo(EPOCH + 1)
            val persistedParameters = serializeCaptor.firstValue
            assertThat(persistedParameters.items.size).isEqualTo(4)
            assertThat(persistedParameters.items.containsAll(
                listOf(
                    KeyValuePair("corda.epoch", "2"),
                    KeyValuePair("corda.notary.service.0.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.0.plugin", KNOWN_NOTARY_PLUGIN),
                    KeyValuePair("corda.notary.service.0.keys.0", "test-key"),
                )
            ))
        }
        assertThat(result).isEqualTo(PersistGroupParametersResponse(EPOCH + 1))
    }

    @Test
    fun `invoke with notary keys adds keys to existing notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
        }
        val memberContext: MemberContext = mock {
            on { get(eq(NOTARY_SERVICE_PARTY_NAME)) } doReturn KNOWN_NOTARY_SERVICE
            on { get(eq(NOTARY_PLUGIN_KEY)) } doReturn KNOWN_NOTARY_PLUGIN
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
            ))
        )

        val result = handler.invoke(requestContext, request)
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))
        with(argumentCaptor<Any>()) {
            verify(entityManager).persist(capture())
            assertThat(firstValue).isInstanceOf(GroupParametersEntity::class.java)
            val entity = firstValue as GroupParametersEntity
            assertThat(entity.epoch).isEqualTo(EPOCH + 1)
            val persistedParameters = serializeCaptor.firstValue
            assertThat(persistedParameters.items.size).isEqualTo(5)
            assertThat(persistedParameters.items.containsAll(
                listOf(
                    KeyValuePair("corda.epoch", "2"),
                    KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                    KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                    KeyValuePair("corda.notary.service.5.keys.0", "existing-test-key"),
                    KeyValuePair("corda.notary.service.5.keys.1", "test-key"),
                )
            ))
        }
        assertThat(result).isEqualTo(PersistGroupParametersResponse(EPOCH + 1))
    }

    @Test
    fun `invoke with nothing to add does nothing`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
        }
        val memberContext: MemberContext = mock {
            on { get(eq(NOTARY_SERVICE_PARTY_NAME)) } doReturn KNOWN_NOTARY_SERVICE
            on { get(eq(NOTARY_PLUGIN_KEY)) } doReturn KNOWN_NOTARY_PLUGIN
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
                KeyValuePair("corda.notary.service.5.keys.0", "test-key")
            ))
        )

        val result = handler.invoke(requestContext, request)
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityManager).transaction
        verify(registry).get(eq(CordaDb.Vault.persistenceUnitName))
        verify(entityManager, times(0)).persist(any())
        assertThat(result).isEqualTo(PersistGroupParametersResponse(EPOCH))
    }

    @Test
    fun `notary plugin must be specified to add new notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
        }
        val memberContext: MemberContext = mock {
            on { get(eq(NOTARY_SERVICE_PARTY_NAME)) } doReturn KNOWN_NOTARY_SERVICE
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(KeyValuePair(EPOCH_KEY, EPOCH.toString())))
        )

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("plugin must be specified")
    }

    @Test
    fun `notary plugin type must must match that of existing notary service`() {
        val knownKey = mock<MemberNotaryKey> {
            on { publicKey } doReturn mock()
        }
        val notaryDetails = mock<MemberNotaryDetails> {
            on { keys } doReturn listOf(knownKey)
        }
        val memberContext: MemberContext = mock {
            on { get(eq(NOTARY_SERVICE_PARTY_NAME)) } doReturn KNOWN_NOTARY_SERVICE
            on { get(eq(NOTARY_PLUGIN_KEY)) } doReturn "incorrect.plugin.type"
            on { entries } doReturn mapOf("$ROLES_PREFIX.0" to NOTARY_ROLE).entries
            on { parse(eq("corda.notary"), eq(MemberNotaryDetails::class.java)) } doReturn notaryDetails
        }
        val mgmContext: MGMContext = mock {
            on { entries } doReturn mapOf("a" to "b").entries
        }
        val notaryInRequest: MemberInfo = mock {
            on { memberProvidedContext } doReturn memberContext
            on { mgmProvidedContext } doReturn mgmContext
        }
        val memberInfoFactory = mock<MemberInfoFactory> {
            on { create(any()) } doReturn notaryInRequest
        }
        whenever(persistenceHandlerServices.memberInfoFactory).doReturn(memberInfoFactory)
        val requestContext = mock<MembershipRequestContext> {
            on { holdingIdentity } doReturn knownIdentity
        }
        val persistentNotary = PersistentMemberInfo(
            knownIdentity,
            notaryInRequest.memberProvidedContext.toWire(),
            notaryInRequest.mgmProvidedContext.toWire()
        )
        val request = mock<AddNotaryToGroupParameters> {
            on { notary } doReturn persistentNotary
        }
        whenever(keyValuePairListDeserializer.deserialize(any())).doReturn(
            KeyValuePairList(mutableListOf(
                KeyValuePair(EPOCH_KEY, EPOCH.toString()),
                KeyValuePair("corda.notary.service.5.name", KNOWN_NOTARY_SERVICE),
                KeyValuePair("corda.notary.service.5.plugin", KNOWN_NOTARY_PLUGIN),
            ))
        )

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("plugin types do not match")
    }
}
