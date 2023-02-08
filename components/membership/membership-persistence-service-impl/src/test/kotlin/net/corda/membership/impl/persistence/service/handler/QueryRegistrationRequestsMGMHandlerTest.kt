package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequestsMGM
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root

class QueryRegistrationRequestsMGMHandlerTest {

    private companion object {
        const val REGISTRATION_ID = "id"
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
    private val holdingIdentityShortHashPath = mock<Path<String>>()
    private val statusPath = mock<Path<String>>()
    private val root = mock<Root<RegistrationRequestEntity>> {
        on { get<String>("holdingIdentityShortHash") } doReturn holdingIdentityShortHashPath
        on { get<String>("status") } doReturn statusPath
    }
    private val query = mock<CriteriaQuery<RegistrationRequestEntity>> {
        on { from(RegistrationRequestEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where() } doReturn mock
        on { where(any()) } doReturn mock
        on { groupBy(holdingIdentityShortHashPath) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(RegistrationRequestEntity::class.java) } doReturn query
        on { `in`(root.get<String>("status")) } doReturn mock()
    }
    private val mockRegistrationRequestEntity = mock<RegistrationRequestEntity> {
        on { registrationId } doReturn REGISTRATION_ID
        on { holdingIdentityShortHash } doReturn "shorthash"
        on { status } doReturn RegistrationStatus.PENDING_MANUAL_APPROVAL.name
        on { created } doReturn mock()
        on { lastModified } doReturn mock()
        on { context } doReturn "dummy".toByteArray()
    }
    private val actualQuery = mock<TypedQuery<RegistrationRequestEntity>> {
        on { resultList } doReturn mutableListOf(mockRegistrationRequestEntity)
    }
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn transaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(query) } doReturn actualQuery
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
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(any()) } doReturn KeyValuePairList(emptyList())
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { cordaAvroSerializationFactory } doReturn serializationFactory
    }
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
    }
    private val request = mock<QueryRegistrationRequestsMGM> {
        on { requestingMemberX500Name } doReturn identity.x500Name.toString()
        on { viewHistoric } doReturn true
    }
    private lateinit var handler: QueryRegistrationRequestsMGMHandler

    @BeforeEach
    fun setUp() {
        handler = QueryRegistrationRequestsMGMHandler(persistenceHandlerServices)
    }

    @Test
    fun `invoke returns the registration requests`() {
        val result = handler.invoke(context, request)

        assertThat(result.registrationRequests.map { it.registrationId }).containsAll(listOf(REGISTRATION_ID))
    }
}
