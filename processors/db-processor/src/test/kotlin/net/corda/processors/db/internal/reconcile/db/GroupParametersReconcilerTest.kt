package net.corda.processors.db.internal.reconcile.db

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.GroupParametersFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.GroupParameters
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.stream.Collectors
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root

class GroupParametersReconcilerTest {
    private val vnode1 = buildVnodeInfo("O=Alice, L=London, C=GB", 0)
    private val vnode2 = buildVnodeInfo("O=Bob, L=London, C=GB", 100)
    private val vnode3 = buildVnodeInfo("O=Charlie, L=London, C=GB", 200)
    private val allVNodesOnStartup = listOf(vnode1, vnode2)

    private val groupParameters: GroupParameters = mock()
    private val avroGroupParameters = KeyValuePairList(listOf(KeyValuePair("foo", "bar")))
    private val serialisedGroupParameters = "foo-bar".toByteArray()
    private val groupParametersEntity = GroupParametersEntity(
        9,
        serialisedGroupParameters
    )

    private val tx1: EntityTransaction = mock()
    private val em1: EntityManager = mock<EntityManager> {
        on { transaction } doReturn tx1
    }.also {
        setUpEntityManagerMocks(it)
    }
    private val emf1: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em1
    }

    private val tx2: EntityTransaction = mock()
    private val em2: EntityManager = mock<EntityManager> {
        on { transaction } doReturn tx2
    }.also {
        setUpEntityManagerMocks(it)
    }
    private val emf2: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em2
    }

    private val tx3: EntityTransaction = mock()
    private val em3: EntityManager = mock<EntityManager> {
        on { transaction } doReturn tx3
    }.also {
        setUpEntityManagerMocks(it)
    }
    private val emf3: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em3
    }

    private val cordaAvroDeserialiser: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(eq(serialisedGroupParameters)) } doReturn avroGroupParameters
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn cordaAvroDeserialiser
    }
    private val coordinator: LifecycleCoordinator = mock()
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(eq(vnode1.vaultDmlConnectionId), any()) } doReturn emf1
        on { createEntityManagerFactory(eq(vnode2.vaultDmlConnectionId), any()) } doReturn emf2
        on { createEntityManagerFactory(eq(vnode3.vaultDmlConnectionId), any()) } doReturn emf3
    }

    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getAll() } doReturn allVNodesOnStartup
    }

    private val entitiesSet: JpaEntitiesSet = mock()
    private val persistenceUnitNameCaptor = argumentCaptor<String>()
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(persistenceUnitNameCaptor.capture()) } doReturn entitiesSet
    }
    private val groupParametersFactory: GroupParametersFactory = mock {
        on { create(eq(avroGroupParameters)) } doReturn groupParameters
    }

    private val groupParametersReconciler = GroupParametersReconciler(
        cordaAvroSerializationFactory,
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
        groupParametersFactory
    )

    @Nested
    inner class BuildReadersPerExistingVNodeTest {
        @Test
        fun `Successfully build reader`() {
            groupParametersReconciler.updateInterval(1000)

            assertThat(groupParametersReconciler.dbReconciler).isNotNull
        }

        @Test
        fun `Reader is not rebuilt if it already exists`() {
            groupParametersReconciler.updateInterval(1000)
            val original = groupParametersReconciler.dbReconciler

            groupParametersReconciler.updateInterval(1000)
            val current = groupParametersReconciler.dbReconciler

            assertThat(original).isEqualTo(current)
        }
    }

    @Nested
    inner class GetVersionedRecordsTest {
        @Test
        fun `get versioned records query acts as expected`() {
            groupParametersReconciler.updateInterval(1000)

            assertThat(groupParametersReconciler.dbReconciler).isNotNull

            val output = groupParametersReconciler.dbReconciler?.getAllVersionedRecords()
            assertThat(output).isNotNull

            val records = output?.collect(Collectors.toList())
            assertThat(records).isNotNull.hasSize(2)

            assertThat(records?.map { it.key }).isNotNull.containsExactlyInAnyOrder(
                vnode1.holdingIdentity,
                vnode2.holdingIdentity
            )
            assertThat(records?.map { it.value }).isNotNull.isEqualTo(
                listOf(groupParameters, groupParameters)
            )
            assertThat(records?.map { it.isDeleted }).isNotNull.isEqualTo(
                listOf(false, false)
            )
            assertThat(records?.map { it.version }).isNotNull.isEqualTo(
                listOf(9, 9)
            )
        }

        @Test
        fun `processing versioned records stream calls expected functions`() {
            groupParametersReconciler.updateInterval(1000)

            groupParametersReconciler.dbReconciler?.getAllVersionedRecords()

            verify(dbConnectionManager, times(2)).createEntityManagerFactory(any(), any())
            verify(em1).criteriaBuilder
            verify(em1).createQuery(any<CriteriaQuery<GroupParametersEntity>>())
            verify(em2).criteriaBuilder
            verify(em2).createQuery(any<CriteriaQuery<GroupParametersEntity>>())

            verify(cordaAvroDeserialiser, times(2)).deserialize(eq(serialisedGroupParameters))
            verify(groupParametersFactory, times(2)).create(eq(avroGroupParameters))
        }

        @Test
        fun `Consuming the versioned records stream closes resources`() {
            groupParametersReconciler.updateInterval(1000)

            val stream = groupParametersReconciler.dbReconciler?.getAllVersionedRecords()
            verify(emf1, never()).close()
            verify(emf2, never()).close()
            verify(em1, never()).close()
            verify(em2, never()).close()
            verify(tx1, never()).rollback()
            verify(tx2, never()).rollback()

            stream?.collect(Collectors.toList())

            verify(emf1).close()
            verify(emf2).close()
            verify(em1).close()
            verify(em2).close()
            verify(tx1).rollback()
            verify(tx2).rollback()
        }
    }

    private fun buildVnodeInfo(
        memberName: String,
        uuidSeed: Long
    ): VirtualNodeInfo {
        var incrementingUuidSeed = uuidSeed
        return VirtualNodeInfo(
            holdingIdentity = HoldingIdentity(
                MemberX500Name.parse(memberName),
                UUID(incrementingUuidSeed++, incrementingUuidSeed++).toString()
            ),
            cpiIdentifier = CpiIdentifier(
                "myCpi.cpi",
                "1.1",
                null
            ),
            vaultDmlConnectionId = UUID(incrementingUuidSeed++, incrementingUuidSeed++),
            cryptoDmlConnectionId = UUID(incrementingUuidSeed++, incrementingUuidSeed++),
            uniquenessDmlConnectionId = UUID(incrementingUuidSeed++, incrementingUuidSeed),
            timestamp = Instant.ofEpochMilli(100)
        )
    }

    private fun setUpEntityManagerMocks(em: EntityManager) {
        val typedQuery: TypedQuery<GroupParametersEntity> = mock {
            on { setMaxResults(any()) } doReturn mock
            on { singleResult } doReturn groupParametersEntity
        }
        val order: Order = mock()
        val path: Path<String> = mock()
        val root: Root<GroupParametersEntity> = mock {
            on { get<String>(eq("epoch")) } doReturn path
        }
        val criteriaQuery: CriteriaQuery<GroupParametersEntity> = mock {
            on { from(eq(GroupParametersEntity::class.java)) } doReturn root
            on { select(eq(root)) } doReturn mock
            on { orderBy(eq(order)) } doReturn mock
        }
        val cb: CriteriaBuilder = mock {
            on { createQuery(eq(GroupParametersEntity::class.java)) } doReturn criteriaQuery
            on { desc(eq(path)) } doReturn order
        }

        whenever(em.criteriaBuilder).thenReturn(cb)
        whenever(em.createQuery(eq(criteriaQuery))).thenReturn(typedQuery)
    }
}