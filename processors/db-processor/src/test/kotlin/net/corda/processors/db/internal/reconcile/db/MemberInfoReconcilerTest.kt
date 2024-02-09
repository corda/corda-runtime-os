package net.corda.processors.db.internal.reconcile.db

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.Resource
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.GroupParametersNotaryUpdater
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.retrieveSignatureSpec
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
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Root
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
        on { createEntityManagerFactory(eq(connectionId), eq(entitySet), any()) } doReturn entityManagerFactory
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
    private val innerReconciler = mock<Reconciler>()

    private val reconcilerFactory = mock<ReconcilerFactory> {
        on {
            create(
                any(),
                any(),
                any(),
                eq(String::class.java),
                eq(PersistentMemberInfo::class.java),
                any(),
                any(),
            )
        } doReturn innerReconciler
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
    private val memberInfoFactory = mock<MemberInfoFactory>()
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
        memberInfoFactory,
    )

    @Nested
    inner class LifecycleTest {

        @Test
        fun `updateInterval will start the reconciler`() {
            memberInfoReconciler.updateInterval(10)

            verify(innerReconciler).start()
        }

        @Test
        fun `second updateInterval will update the reconciler interval`() {
            memberInfoReconciler.updateInterval(10)
            memberInfoReconciler.updateInterval(20)

            verify(innerReconciler).updateInterval(20)
        }

        @Test
        fun `second updateInterval will not start the reconciler again`() {
            memberInfoReconciler.updateInterval(10)
            memberInfoReconciler.updateInterval(30)

            verify(innerReconciler, times(1)).start()
        }

        @Test
        fun `stop will stop the reconciler`() {
            memberInfoReconciler.updateInterval(10)

            memberInfoReconciler.stop()

            verify(innerReconciler).stop()
        }

        @Test
        fun `stop will not stop the reconciler is not started`() {
            memberInfoReconciler.stop()

            verify(innerReconciler, never()).stop()
        }

        @Test
        fun `StartEvent will follow the configuration read service`() {
            handler.firstValue.processEvent(StartEvent(), coordinator)

            verify(coordinator).followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                )
            )
        }

        @Test
        fun `StopEvent will close managed resources`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator).closeManagedResources(
                argThat {
                    size == 4
                }
            )
        }

        @Test
        fun `StopEvent will set the status to down`() {
            handler.firstValue.processEvent(StopEvent(), coordinator)

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `child up will wait for config`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(configurationReadService).registerComponentForUpdates(
                coordinator,
                setOf(
                    ConfigKeys.BOOT_CONFIG,
                    ConfigKeys.MESSAGING_CONFIG,
                )
            )
        }

        @Test
        fun `child down will set the status to down`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `child down will close the resource`() {
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    mock(),
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).closeManagedResources(argThat { size == 1 })
        }

        @Test
        fun `config changed event will create the publisher`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(publisherFactory).createPublisher(any(), eq(messagingConfig))
        }

        @Test
        fun `config changed event will start the publisher`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(publisher).start()
        }

        @Test
        fun `config changed event will create the subscription`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(subscriptionFactory).createCompactedSubscription(
                any(),
                eq(processor.firstValue),
                eq(messagingConfig),
            )
        }

        @Test
        fun `config changed event will start the subscription`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            verify(compactedSubscription).start()
        }
    }

    @Nested
    inner class ReaderTests {

        @Test
        fun `onNext adds an item to the list`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
            val key = "Key"
            val serialNumber = 26

            val mgmContextBytes = ByteBuffer.wrap(byteArrayOf(1))
            whenever(deserializer.deserialize(mgmContextBytes.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, serialNumber.toString()))))
            val memberInfo = mock<PersistentMemberInfo> {
                on { signedMemberContext } doReturn mock()
                on { serializedMgmContext } doReturn mgmContextBytes
            }
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            val records = memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords()

            assertThat(records).hasSize(1)
                .anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key).isEqualTo(key)
                    assertThat(it.version).isEqualTo(serialNumber)
                    assertThat(it.value).isEqualTo(memberInfo)
               }
        }

        @Test
        fun `onSnapshot adds items to the list`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            val key = "Key"
            val serialNumber = 26

            val key1 = "Key1"
            val serialNumber1 = 32

            val mgmContextBytes = ByteBuffer.wrap(byteArrayOf(1))
            val mgmContextBytes1 = ByteBuffer.wrap(byteArrayOf(2))
            whenever(deserializer.deserialize(mgmContextBytes.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, serialNumber.toString()))))
            whenever(deserializer.deserialize(mgmContextBytes1.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, serialNumber1.toString()))))
            val memberInfo = mock<PersistentMemberInfo> {
                on { signedMemberContext } doReturn mock()
                on { serializedMgmContext } doReturn mgmContextBytes
            }
            val memberInfo1 =  mock<PersistentMemberInfo> {
                on { signedMemberContext } doReturn mock()
                on { serializedMgmContext } doReturn mgmContextBytes1
            }
            processor.firstValue.onSnapshot(mapOf(key to memberInfo, key1 to memberInfo1))

            val records = memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords()

            assertThat(records.toList()).hasSize(2)
                .anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key).isEqualTo(key)
                    assertThat(it.version).isEqualTo(serialNumber)
                    assertThat(it.value).isEqualTo(memberInfo)
                }.anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key).isEqualTo(key1)
                    assertThat(it.version).isEqualTo(serialNumber1)
                    assertThat(it.value).isEqualTo(memberInfo1)
                }
        }

        @Test
        fun `getAllVersionedRecords handles old version of persistent info`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)
            val key = "Key"
            val serialNumber = 26

            val memberInfo = mock<PersistentMemberInfo> {
                on { mgmContext } doReturn KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, serialNumber.toString())))
                on { signedMemberContext } doReturn null
                on { serializedMgmContext } doReturn null
            }
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            val records = memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords()

            assertThat(records).hasSize(1)
                .anySatisfy {
                    assertThat(it.isDeleted).isFalse
                    assertThat(it.key).isEqualTo(key)
                    assertThat(it.version).isEqualTo(serialNumber)
                    assertThat(it.value).isEqualTo(memberInfo)
                }
        }

        @Test
        fun `name parsing when exception is thrown handles old version of persistent info`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            val key = "Key"
            val memberInfo = mock<PersistentMemberInfo> {
                on { viewOwningMember } doReturn AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID")
                on { memberContext } doReturn KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, "Alice")))
                on { mgmContext } doReturn KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, "ABCD")))
                on { signedMemberContext } doReturn null
                on { serializedMgmContext } doReturn null
            }
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            assertThat(memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords().toList()).isEmpty()
        }

        @Test
        fun `getAllVersionedRecords handles NumberFormatException`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            val key = "Key"
            val memberContextBytes = ByteBuffer.wrap(byteArrayOf(0))
            val signedData = mock<SignedData> {
                on { data } doReturn memberContextBytes
            }
            val mgmContextBytes = ByteBuffer.wrap(byteArrayOf(1))
            whenever(deserializer.deserialize(memberContextBytes.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, "Alice"))))
            whenever(deserializer.deserialize(mgmContextBytes.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, "ABCD"))))
            val memberInfo = mock<PersistentMemberInfo> {
                on { viewOwningMember } doReturn AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID")
                on { signedMemberContext } doReturn signedData
                on { serializedMgmContext } doReturn mgmContextBytes
            }
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            assertThat(memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords().toList()).isEmpty()
        }

        @Test
        fun `getAllVersionedRecords handles no serial number`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            val key = "Key"
            val memberContextBytes = ByteBuffer.wrap(byteArrayOf(0))
            val signedData = mock<SignedData> {
                on { data } doReturn memberContextBytes
            }
            val mgmContextBytes = ByteBuffer.wrap(byteArrayOf(1))
            whenever(deserializer.deserialize(memberContextBytes.array()))
                .doReturn(KeyValuePairList(emptyList()))
            whenever(deserializer.deserialize(mgmContextBytes.array()))
                .doReturn(KeyValuePairList(emptyList()))
            val memberInfo = mock<PersistentMemberInfo> {
                on { viewOwningMember } doReturn AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID")
                on { signedMemberContext } doReturn signedData
                on { serializedMgmContext } doReturn mgmContextBytes
            }
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            assertThat(memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords().toList()).isEmpty()
        }

        @Test
        fun `getAllVersionedRecords handles ContextDeserializationException`() {
            handler.firstValue.processEvent(configChangedEvent, coordinator)

            val key = "Key"
            val memberContextBytes = ByteBuffer.wrap(byteArrayOf(0))
            val signedData = mock<SignedData> {
                on { data } doReturn memberContextBytes
            }
            val mgmContextBytes = ByteBuffer.wrap(byteArrayOf(1))
            whenever(deserializer.deserialize(memberContextBytes.array()))
                .doReturn(KeyValuePairList(emptyList()))
            whenever(deserializer.deserialize(mgmContextBytes.array()))
                .doReturn(null)
            val memberInfo = mock<PersistentMemberInfo> {
                on { viewOwningMember } doReturn AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID")
                on { signedMemberContext } doReturn signedData
                on { serializedMgmContext } doReturn mgmContextBytes
            }
            processor.firstValue.onNext(Record("topic", key, memberInfo), null, emptyMap())

            assertThat(memberInfoReconciler.reconcilerReadWriter.getAllVersionedRecords().toList()).isEmpty()
        }
    }

    @Nested
    inner class WriterTests {
        @Test
        fun `put will publish the record`() {
            val key = "KEY"
            val memberContextBytes = ByteBuffer.wrap(byteArrayOf(0))
            val signedData = mock<SignedData> {
                on { data } doReturn memberContextBytes
            }
            val mgmContextBytes = ByteBuffer.wrap(byteArrayOf(1))
            whenever(deserializer.deserialize(memberContextBytes.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(PARTY_NAME, "Alice"))))
            whenever(deserializer.deserialize(mgmContextBytes.array()))
                .doReturn(KeyValuePairList(listOf(KeyValuePair(MemberInfoExtension.SERIAL, "ABCD"))))
            val memberInfo = mock<PersistentMemberInfo> {
                on { viewOwningMember } doReturn AvroHoldingIdentity("O=Alice, L=London, C=GB", "GROUP_ID")
                on { signedMemberContext } doReturn signedData
                on { serializedMgmContext } doReturn mgmContextBytes
            }
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

    @Nested
    inner class QueryTests {
        @Test
        fun `getAllMemberInfo will return all the member info`() {
            val group = "group"
            val viewOwningMember = HoldingIdentity(
                MemberX500Name.parse("C=GB, CN=MGM, O=MGM Corp, L=LDN"),
                "group"
            )
            val reconcilierVirtualNodeInfo = mock<VirtualNodeInfo> {
                on { holdingIdentity } doReturn viewOwningMember
            }
            val virtualNodeReconcilationContext = mock<VirtualNodeReconciliationContext> {
                on { getOrCreateEntityManager() } doReturn entityManager
                on { virtualNodeInfo } doReturn reconcilierVirtualNodeInfo
            }
            val root = mock<Root<MemberInfoEntity>>()
            val queryBuilder = mock<CriteriaQuery<MemberInfoEntity>> {
                on { from(MemberInfoEntity::class.java) } doReturn root
                on { select(root) } doReturn mock
            }
            val criteriaBuilder = mock<CriteriaBuilder> {
                on { createQuery(MemberInfoEntity::class.java) } doReturn queryBuilder
            }
            val firstX500Name = "O=Alice, L=London, C=GB"
            val firstSerial = 106L
            val firstMemberContext = byteArrayOf(1, 2)
            val firstMgmContext = byteArrayOf(3, 4)
            val signatureKey = byteArrayOf(1)
            val signatureContent = byteArrayOf(2)
            val signatureSpec = "test"
            val firstMockMemberInfoEntity = mock<MemberInfoEntity> {
                on { memberX500Name } doReturn firstX500Name
                on { memberContext } doReturn firstMemberContext
                on { memberSignatureKey } doReturn signatureKey
                on { memberSignatureContent } doReturn signatureContent
                on { memberSignatureSpec } doReturn signatureSpec
                on { mgmContext } doReturn firstMgmContext
                on { serialNumber } doReturn firstSerial
                on { isDeleted } doReturn true
            }
            val secondX500Name = "O=Bob, L=London, C=GB"
            val secondSerial = 42L
            val secondMemberConetxt = byteArrayOf(5, 6)
            val secondMgmContext = byteArrayOf(7, 8)
            val secondMockMemberInfoEntity = mock<MemberInfoEntity> {
                on { memberX500Name } doReturn secondX500Name
                on { memberContext } doReturn secondMemberConetxt
                on { memberSignatureKey } doReturn signatureKey
                on { memberSignatureContent } doReturn signatureContent
                on { memberSignatureSpec } doReturn signatureSpec
                on { mgmContext } doReturn secondMgmContext
                on { serialNumber } doReturn secondSerial
                on { isDeleted } doReturn false
            }
            val reply = mock<TypedQuery<MemberInfoEntity>> {
                on { resultStream } doReturn listOf(
                    firstMockMemberInfoEntity,
                    secondMockMemberInfoEntity,
                ).stream()
            }
            whenever(entityManager.criteriaBuilder).doReturn(criteriaBuilder)
            whenever(entityManager.createQuery(queryBuilder)).doReturn(reply)
            val memberSignature = CryptoSignatureWithKey(ByteBuffer.wrap(signatureKey), ByteBuffer.wrap(signatureContent))
            val memberSignatureSpec = retrieveSignatureSpec(signatureSpec)
            whenever(
                memberInfoFactory.createPersistentMemberInfo(
                    viewOwningMember.toAvro(), firstMemberContext, firstMgmContext, signatureKey, signatureContent, signatureSpec
                )
            ).doReturn(
                PersistentMemberInfo(
                    viewOwningMember.toAvro(),
                    null,
                    null,
                    SignedData(ByteBuffer.wrap(firstMemberContext), memberSignature, memberSignatureSpec),
                    ByteBuffer.wrap(firstMgmContext),
                )
            )
            whenever(
                memberInfoFactory.createPersistentMemberInfo(
                    viewOwningMember.toAvro(), secondMemberConetxt, secondMgmContext, signatureKey, signatureContent, signatureSpec
                )
            ).doReturn(
                PersistentMemberInfo(
                    viewOwningMember.toAvro(),
                    null,
                    null,
                    SignedData(ByteBuffer.wrap(secondMemberConetxt), memberSignature, memberSignatureSpec),
                    ByteBuffer.wrap(secondMgmContext),
                )
            )

            memberInfoReconciler.updateInterval(10)

            val records = MemberInfoReconciler.getAllMemberInfo(virtualNodeReconcilationContext, memberInfoFactory)

            assertThat(records).hasSize(2)
                .anySatisfy {
                    assertThat(it.value.viewOwningMember).isEqualTo(viewOwningMember.toAvro())
                    assertThat(it.value.signedMemberContext.data.array()).isEqualTo(firstMemberContext)
                    assertThat(it.value.serializedMgmContext.array()).isEqualTo(firstMgmContext)
                    assertThat(it.value.signedMemberContext.signature).isEqualTo(memberSignature)
                    assertThat(it.value.signedMemberContext.signatureSpec).isEqualTo(memberSignatureSpec)
                    assertThat(it.version).isEqualTo(firstSerial)
                    assertThat(it.key)
                        .isEqualTo("${viewOwningMember.shortHash}-${HoldingIdentity(MemberX500Name.parse(firstX500Name), group).shortHash}")
                    assertThat(it.isDeleted).isEqualTo(true)
                 }
                .anySatisfy {
                    assertThat(it.value.viewOwningMember).isEqualTo(viewOwningMember.toAvro())
                    assertThat(it.value.signedMemberContext.data.array()).isEqualTo(secondMemberConetxt)
                    assertThat(it.value.serializedMgmContext.array()).isEqualTo(secondMgmContext)
                    assertThat(it.value.signedMemberContext.signature).isEqualTo(memberSignature)
                    assertThat(it.value.signedMemberContext.signatureSpec).isEqualTo(memberSignatureSpec)
                    assertThat(it.key)
                        .isEqualTo(
                            "${viewOwningMember.shortHash}-${HoldingIdentity(MemberX500Name.parse(secondX500Name), group).shortHash}"
                        )
                    assertThat(it.version).isEqualTo(secondSerial)
                    assertThat(it.isDeleted).isEqualTo(false)
                }
        }

        @Test
        fun `getAllMemberInfo creates the correct query`() {
            val virtualNodeReconcilationContext = mock<VirtualNodeReconciliationContext> {
                on { getOrCreateEntityManager() } doReturn entityManager
            }
            val root = mock<Root<MemberInfoEntity>>()
            val queryBuilder = mock<CriteriaQuery<MemberInfoEntity>> {
                on { from(MemberInfoEntity::class.java) } doReturn root
                on { select(root) } doReturn mock
            }
            val criteriaBuilder = mock<CriteriaBuilder> {
                on { createQuery(MemberInfoEntity::class.java) } doReturn queryBuilder
            }
            whenever(entityManager.criteriaBuilder).doReturn(criteriaBuilder)
            whenever(entityManager.createQuery(queryBuilder)).doReturn(mock())

            MemberInfoReconciler.getAllMemberInfo(virtualNodeReconcilationContext, memberInfoFactory)

            verify(entityManager).createQuery(queryBuilder)
        }

    }
}