package net.corda.processors.db.internal.reconcile.db

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import javax.persistence.EntityManager
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.deserializeContext
import net.corda.membership.lib.toSortedMap
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.reconciliation.VersionedRecord
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.VisibleForTesting
import net.corda.utilities.mapNotNull
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class MemberInfoReconciler(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val reconcilerFactory: ReconcilerFactory,
    private val publisherFactory: PublisherFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val configurationReadService: ConfigurationReadService,
    private val memberInfoFactory: MemberInfoFactory,
): ReconcilerWrapper {
    companion object {
        private const val FOLLOW_CHANGES_RESOURCE_NAME = "MemberInfoReconcilierReadWriter.followStatusChangesByName"
        private const val WAIT_FOR_CONFIG_RESOURCE_NAME = "MemberInfoReconcilierReadWriter.registerComponentForUpdates"
        private const val SUBSCRIPTION_RESOURCE_NAME = "MemberInfoReconcilierReadWriter.subscription"
        internal const val PUBLISHER_RESOURCE_NAME = "MemberInfoReconcilierReadWriter.publisher"
        private const val PUBLISHER_CLIENT_ID = "member-info-reconcilier-writer"

        private val dependencies = setOf(
            LifecycleCoordinatorName.forComponent<DbConnectionManager>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
        )
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private fun getAllMemberInfo(em: EntityManager): Stream<MemberInfoEntity> {
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(MemberInfoEntity::class.java)
            val root = queryBuilder.from(MemberInfoEntity::class.java)
            val query = queryBuilder.select(root)
            return em.createQuery(query).resultStream
        }

        @VisibleForTesting
        internal fun getAllMemberInfo(
            reconciliationContext: ReconciliationContext,
            memberInfoFactory: MemberInfoFactory
        ): Stream<VersionedRecord<String, PersistentMemberInfo>> {
            val context = reconciliationContext as? VirtualNodeReconciliationContext ?: return Stream.empty()
            return getAllMemberInfo(context.getOrCreateEntityManager()).map { entity ->
                val memberInfo = memberInfoFactory.createPersistentMemberInfo(
                    context.virtualNodeInfo.holdingIdentity.toAvro(),
                    entity.memberContext,
                    entity.mgmContext,
                    entity.memberSignatureKey,
                    entity.memberSignatureContent,
                    entity.memberSignatureSpec,
                )
                val holdingIdentity = HoldingIdentity(
                    MemberX500Name.parse(entity.memberX500Name), context.virtualNodeInfo.holdingIdentity.groupId
                )
                object : VersionedRecord<String, PersistentMemberInfo> {
                    override val version = entity.serialNumber.toInt()
                    override val isDeleted = entity.isDeleted
                    override val key = "${context.virtualNodeInfo.holdingIdentity.shortHash}-${holdingIdentity.shortHash}"
                    override val value = memberInfo
                }
            }
        }
    }
    private val entitiesSet by lazy {
        jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
            ?: throw CordaRuntimeException(
                "persistenceUnitName '${CordaDb.Vault.persistenceUnitName}' is not registered."
            )
    }
    private var dbReconcilerReader: DbReconcilerReader<String, PersistentMemberInfo>? = null
    private var reconciler: Reconciler? = null
    private fun reconciliationContextFactory() = virtualNodeInfoReadService.getAll().stream().map {
            VirtualNodeReconciliationContext(dbConnectionManager, entitiesSet, it)
        }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )
    }
    @VisibleForTesting
    internal val reconcilerReadWriter = MemberInfoReconcilerReadWriter()

    internal inner class MemberInfoReconcilerReadWriter:
        ReconcilerWriter<String, PersistentMemberInfo>, ReconcilerReader<String, PersistentMemberInfo>,  Lifecycle {

        override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<MemberInfoReconcilerReadWriter>()
        private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, ::handleEvent)
        private val recordsMap = ConcurrentHashMap<String, PersistentMemberInfo>()

        override fun getAllVersionedRecords(): Stream<VersionedRecord<String, PersistentMemberInfo>> {
            return recordsMap.entries.stream().mapNotNull {
                val version = try {
                    // we can have old schema version (5.0) of persistent information in case of platform upgrade
                    if (it.value.mgmContext == null ) {
                        it.value.serializedMgmContext.array().deserializeContext(keyValuePairListDeserializer)
                            .getOrDefault(SERIAL, null)?.toInt()
                    } else {
                        it.value.mgmContext.items.singleOrNull { keyValuePair -> keyValuePair.key == SERIAL }?.value?.toInt()
                    }
                } catch (e: ContextDeserializationException) {
                    val name = parseName(it.value)
                    logger.warn("Could not deserialize mgm provided context in " +
                            "record for $name in ${it.value.viewOwningMember}'s list.")
                    return@mapNotNull null
                } catch (e: NumberFormatException) {
                    val name = parseName(it.value)
                    logger.warn("Record for $name in ${it.value.viewOwningMember}'s member " +
                            "list doesn't contain a valid serial number.")
                    return@mapNotNull null
                }
                if (version == null) {
                    val name = parseName(it.value)
                    logger.warn("Record for $name in ${it.value.viewOwningMember}'s member " +
                        "list doesn't contain a serial number.")
                    return@mapNotNull null
                }
                object: VersionedRecord<String, PersistentMemberInfo> {
                    override val version: Int = version
                    override val isDeleted = false
                    override val key = it.key
                    override val value = it.value
                }
            }
        }

        override fun put(recordKey: String, recordValue: PersistentMemberInfo) {
            val name = parseName(recordValue)
            logger.info("Reconciling record for: $name in " +
                    "${recordValue.viewOwningMember}'s member list.")
            coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.publish(
                listOf(Record(topic = MEMBER_LIST_TOPIC, key = recordKey, value = recordValue))
            )
        }

        override fun remove(recordKey: String) {
            coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.publish(
                listOf(Record(topic = MEMBER_LIST_TOPIC, key = recordKey, value = null))
            )
        }

        private fun parseName(memberInfo: PersistentMemberInfo): String? {
            return try {
                // we can have old schema version (5.0) of persistent information in case of platform upgrade
                if (memberInfo.memberContext == null) {
                    memberInfo.signedMemberContext.data.array().deserializeContext(keyValuePairListDeserializer)
                        .getOrDefault(PARTY_NAME, null)
                } else {
                    memberInfo.memberContext.toSortedMap()[PARTY_NAME]
                }
            } catch (e: ContextDeserializationException) {
                logger.warn("Could not retrieve name from member provided context. " +
                        "Deserialization of the context failed.")
                null
            }
        }

        private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            logger.info("Received event $event.")
            when (event) {
                is StartEvent -> {
                    coordinator.createManagedResource(FOLLOW_CHANGES_RESOURCE_NAME) {
                        coordinator.followStatusChangesByName(
                            setOf(
                                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            )
                        )
                    }
                }

                is StopEvent -> {
                    coordinator.closeManagedResources(
                        setOf(
                            FOLLOW_CHANGES_RESOURCE_NAME,
                            WAIT_FOR_CONFIG_RESOURCE_NAME,
                            SUBSCRIPTION_RESOURCE_NAME,
                            PUBLISHER_RESOURCE_NAME,
                        )
                    )
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }

                is RegistrationStatusChangeEvent -> {
                    if (event.status == LifecycleStatus.UP) {
                        coordinator.createManagedResource(WAIT_FOR_CONFIG_RESOURCE_NAME) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                setOf(
                                    ConfigKeys.BOOT_CONFIG,
                                    ConfigKeys.MESSAGING_CONFIG,
                                )
                            )
                        }
                    } else {
                        coordinator.closeManagedResources(
                            setOf(
                                WAIT_FOR_CONFIG_RESOURCE_NAME
                            )
                        )
                        coordinator.updateStatus(LifecycleStatus.DOWN)
                    }
                }

                is ConfigChangedEvent -> {
                    val messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    coordinator.createManagedResource(PUBLISHER_RESOURCE_NAME) {
                        publisherFactory.createPublisher(
                            messagingConfig = messagingConfig,
                            publisherConfig = PublisherConfig(PUBLISHER_CLIENT_ID)
                        ).also {
                            it.start()
                        }
                    }
                    coordinator.createManagedResource(SUBSCRIPTION_RESOURCE_NAME) {
                        subscriptionFactory.createCompactedSubscription(
                            subscriptionConfig =SubscriptionConfig(SUBSCRIPTION_RESOURCE_NAME, MEMBER_LIST_TOPIC),
                            processor = Processor(recordsMap),
                            messagingConfig = messagingConfig,
                        ).also {
                            it.start()
                        }
                    }
                }
            }
        }

        private inner class Processor(
            val recordsMap: MutableMap<String, PersistentMemberInfo>
        ): CompactedProcessor<String, PersistentMemberInfo> {

            override val keyClass = String::class.java
            override val valueClass = PersistentMemberInfo::class.java

            override fun onNext(
                newRecord: Record<String, PersistentMemberInfo>,
                oldValue: PersistentMemberInfo?,
                currentData: Map<String, PersistentMemberInfo>
            ) {
                newRecord.value?.let { recordsMap[newRecord.key] = it } ?: recordsMap.remove(newRecord.key)
            }

            override fun onSnapshot(currentData: Map<String, PersistentMemberInfo>) {
                currentData.forEach {
                    recordsMap[it.key] = it.value
                }
                coordinator.updateStatus(LifecycleStatus.UP)
            }

        }

        override val isRunning: Boolean
            get() = coordinator.status == LifecycleStatus.UP

        override fun start() {
            coordinator.start()
        }

        override fun stop() {
            coordinator.stop()
        }
    }

    override fun updateInterval(intervalMillis: Long) {

        if (dbReconcilerReader == null) {
            dbReconcilerReader = DbReconcilerReader(
                coordinatorFactory,
                String::class.java,
                PersistentMemberInfo::class.java,
                dependencies,
                ::reconciliationContextFactory
            ) { em -> getAllMemberInfo(em, memberInfoFactory) }.also {
                it.start()
            }
        }
        if (!reconcilerReadWriter.isRunning) {
            reconcilerReadWriter.start()
        }
        reconciler = reconciler.let { reconciler ->
            if (reconciler == null) {
                reconcilerFactory.create(
                    dbReader = dbReconcilerReader!!,
                    kafkaReader = reconcilerReadWriter,
                    writer = reconcilerReadWriter,
                    keyClass = String::class.java,
                    valueClass = PersistentMemberInfo::class.java,
                    reconciliationIntervalMs = intervalMillis,
                ).also { it.start() }
            } else {
                reconciler.updateInterval(intervalMillis)
                reconciler
            }
        }
    }

    override fun stop() {
        dbReconcilerReader?.stop()
        dbReconcilerReader = null
        reconciler?.stop()
        reconciler = null
        reconcilerReadWriter.stop()
    }
}