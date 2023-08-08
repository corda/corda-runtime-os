package net.corda.processors.db.internal.reconcile.db

import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.Resource
import net.corda.membership.lib.GroupParametersNotaryUpdater
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity

class MemberInfoReconcilerTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val name = mock<LifecycleCoordinatorName>()
    private val publisher = mock<Publisher>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { createManagedResource(any(), any<() -> Resource>()) } doAnswer {
            val function: () -> Resource = it.getArgument(1)
            function.invoke()
        }
        on { getManagedResource<Publisher>(MemberInfoReconciler.PUBLISHER_RESOURCE_NAME) } doReturn publisher
        on { name } doReturn name
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val connectionId = UUID(0, 0)
    private val transaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn transaction
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { createEntityManagerFactory(connectionId, entitySet) } doReturn entityManagerFactory
    }
    private val virtualNodeInfo = mock<VirtualNodeInfo> {
        on { vaultDmlConnectionId } doReturn connectionId
        on { holdingIdentity } doReturn HoldingIdentity(
            MemberX500Name.parse("C=GB, CN=Alice, O=Alice Corp, L=LDN"),
            "Group ID"
        )
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getAll() } doReturn listOf(virtualNodeInfo)
    }
    private val dbReader = argumentCaptor<DbReconcilerReader<String, PersistentMemberInfo>>()
    private val reconciler = mock<Reconciler>()

    private val reconcilerFactory = mock<ReconcilerFactory> {
        on {
            create(
                dbReader.capture(),
                any(),
                any(),
                eq(String::class.java),
                eq(PersistentMemberInfo::class.java),
                any(),
            )
        } doReturn reconciler
    }

    private val deserializedParams = KeyValuePairList(listOf(KeyValuePair(GroupParametersNotaryUpdater.EPOCH_KEY, "1")))
    private val serializedParams = "group-parameters-1".toByteArray()
    private val deserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(serializedParams) } doReturn deserializedParams
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn deserializer
    }
    private val configurationReadService = mock<ConfigurationReadService>()
    private val compactedSubscription = mock<CompactedSubscription<String, PersistentMemberInfo>>()
    private val processor = argumentCaptor<CompactedProcessor<String, PersistentMemberInfo>>()
    private val subscriptionFactory = mock<SubscriptionFactory> {
        on {
            createCompactedSubscription(
                any(),
                processor.capture(),
                any()
            )
        } doReturn compactedSubscription
    }
    private val messagingConfig = mock<SmartConfig>()
    private val configChangedEvent = mock<ConfigChangedEvent> {
        on { config } doReturn mapOf(ConfigKeys.MESSAGING_CONFIG to messagingConfig)
    }
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), eq(messagingConfig)) } doReturn publisher
    }
    private val memberInfoReconciler = MemberInfoReconciler(
        coordinatorFactory,
        dbConnectionManager,
        virtualNodeInfoReadService,
        jpaEntitiesRegistry,
        serializationFactory,
        reconcilerFactory,
        publisherFactory,
        subscriptionFactory,
        configurationReadService,
    )

    @Nested
    inner class LifecycleTest {

        @Test
        fun `updateInterval will start the reconciler`() {
            memberInfoReconciler.updateInterval(10)

            verify(reconciler).start()
        }

        @Test
        fun `second updateInterval will update the reconciler interval`() {
            memberInfoReconciler.updateInterval(10)
            memberInfoReconciler.updateInterval(20)

            verify(reconciler).updateInterval(20)
        }

        @Test
        fun `second updateInterval will not start the reconciler again`() {
            memberInfoReconciler.updateInterval(10)
            memberInfoReconciler.updateInterval(30)

            verify(reconciler, times(1)).start()
        }

        @Test
        fun `stop will stop the reconciler`() {
            memberInfoReconciler.updateInterval(10)

            memberInfoReconciler.stop()

            verify(reconciler).stop()
        }

        @Test
        fun `stop will not stop the reconciler is not started`() {
            memberInfoReconciler.stop()

            verify(reconciler, never()).stop()
        }
    }

    @Nested
    inner class ReaderTests {

        @Test
        fun `onNext adds an item to the list`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
            processor.firstValue.onSnapshot(emptyMap())
            val key = "Key"
            val serialNumber = 26

            val memberInfo = PersistentMemberInfo(
                null,
                null,
                KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, serialNumber.toString())))
            )
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            val records = memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords()

            Assertions.assertThat(records).hasSize(1)
                .anySatisfy {
                    Assertions.assertThat(it.isDeleted).isFalse
                    Assertions.assertThat(it.key).isEqualTo(key)
                    Assertions.assertThat(it.version).isEqualTo(serialNumber)
                    Assertions.assertThat(it.value).isEqualTo(memberInfo)
               }
        }

        @Test
        fun `getAllVersionedRecords handles NumberFormatException`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
            processor.firstValue.onSnapshot(emptyMap())

            val key = "Key"
            val memberContext = KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, "Alice")))
            val memberInfo = PersistentMemberInfo(
                AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID"),
                memberContext,
                KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, "ABCD")))
            )
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            Assertions.assertThat(memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords()?.toList()).isEmpty()
        }

        @Test
        fun `getAllVersionedRecords handles no serial number`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
            processor.firstValue.onSnapshot(emptyMap())

            val key = "Key"
            val memberContext = KeyValuePairList(emptyList())
            val memberInfo = PersistentMemberInfo(
                AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID"),
                memberContext,
                KeyValuePairList(emptyList())
            )
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            Assertions.assertThat(memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords()?.toList()).isEmpty()
        }
    }

    @Nested
    inner class WriterTests {
        @Test
        fun `put will publish the record`() {
            val key = "KEY"
            val memberContext = KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, "Alice")))
            val memberInfo = PersistentMemberInfo(
                AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID"),
                memberContext,
                KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, "ABCD")))
            )
            memberInfoReconciler.reconcilerReadWriter.put(key, memberInfo)

            verify(publisher).publish(
                argThat {
                    size == 1 && first().topic == MEMBER_LIST_TOPIC && first().key == key && first().value == memberInfo
                }
            )
        }

        @Test
        fun `remove will publish a tombstone`() {
            val key = "KEY"
            memberInfoReconciler.reconcilerReadWriter.remove(key)

            verify(publisher).publish(
                argThat {
                    size == 1 && first().topic == MEMBER_LIST_TOPIC && first().key == key && first().value == null
                }
            )
        }
    }
}