package net.corda.metrics

import io.micrometer.core.instrument.Tag as micrometerTag
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.BaseUnits
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.noop.NoopGauge
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.function.Supplier
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction


object CordaMetrics {
    sealed class Metric<T : Meter>(
        val metricsName: String,
        private val meter: (String, Iterable<micrometerTag>) -> T
    ) {

        fun builder(): MeterBuilder<T> {
            return MeterBuilder(metricsName, meter)
        }

        // NOTE: please ensure the metric names adhere to the conventions described on https://micrometer.io/docs/concepts#_naming_meters

        /**
         * HTTP Requests time.
         */
        object HttpRequestTime : Metric<Timer>("http.server.request.time", CordaMetrics::timer)

        /**
         * Time it took to create the sandbox
         */
        object SandboxCreateTime : Metric<Timer>("sandbox.create.time", CordaMetrics::timer)

        /**
         * FLOW METRICS
         *
         * Time it took for a flow or subFlow to complete successfully or to error.
         */
        object FlowRunTime : Metric<Timer>("flow.run.time", CordaMetrics::timer)

        /**
         * Metric for flow or subFlow fiber serialization.
         */
        object FlowFiberSerializationTime : Metric<Timer>("flow.fiber.serialization.time", CordaMetrics::timer)

        /**
         * Metric for flow or subFlow fiber deserialization.
         */
        object FlowFiberDeserializationTime : Metric<Timer>("flow.fiber.deserialization.time", CordaMetrics::timer)

        /**
         * Metric for lag between flow start event between the REST API and the flow processor.
         */
        object FlowStartLag : Metric<Timer>("flow.start.lag", CordaMetrics::timer)

        /**
         * Metric for the time taken to execute the flow (excluding any start lag).
         *
         * Total count of flows executed can be obtained by the number of events recorded for this metric.
         */
        object FlowExecutionTime : Metric<Timer>("flow.execution.time", CordaMetrics::timer)

        /**
         * Metric for lag between flow event publication and processing.
         */
        object FlowEventLagTime : Metric<Timer>("flow.event.lag", CordaMetrics::timer)

        /**
         * Metric for the time taken to process a single event in the flow pipeline.
         *
         * Number of pipeline events processed can be inferred from the count of events recorded for this metric.
         */
        object FlowEventPipelineExecutionTime : Metric<Timer>("flow.event.pipeline.execution.time", CordaMetrics::timer)


        /**
         * Metric for the time the fiber is running between two suspension points.
         *
         * Number of fiber execution events processed can be inferred from the count of events recorded for this metric.
         */
        object FlowEventFiberExecutionTime : Metric<Timer>("flow.event.fiber.execution.time", CordaMetrics::timer)


        /**
         * Metric for the total time spent in the pipeline code across the execution time of a flow or subFlow.
         */
        object FlowPipelineExecutionTime : Metric<Timer>("flow.pipeline.execution.time", CordaMetrics::timer)

        /**
         * Metric for the total time spent executing user code across the execution time of a flow or subFlow.
         */
        object FlowFiberExecutionTime : Metric<Timer>("flow.fiber.execution.time", CordaMetrics::timer)


        /**
         * Metric for total time taken waiting to awake from a suspension
         */
        object FlowSuspensionWaitTime : Metric<Timer>("flow.suspension.wait.time", CordaMetrics::timer)

        /**
         * Metric for the time taken waiting to awake from a suspension
         *
         * Number of flow event suspensions can be inferred from the count of events recorded for this metric.
         */
        object FlowEventSuspensionWaitTime : Metric<Timer>("flow.event.suspension.wait.time", CordaMetrics::timer)

        /**
         * Number of times a scheduled wakeup is published for flows and subFlows.
         */
        object FlowScheduledWakeupCount : Metric<Counter>("flow.scheduled.wakeup.count", Metrics::counter)

        /**
         * Number of events a flow received in order for it to complete.
         */
        object FlowEventProcessedCount : Metric<DistributionSummary>("flow.event.processed.count", Metrics::summary)

        /**
         * Number of flow events that lead to a fiber resume for a single flow or subFlow.
         */
        object FlowFiberSuspensionCount : Metric<DistributionSummary>("flow.fiber.suspension.total.count", Metrics::summary)

        /**
         * FLOW MAPPER METRICS
         *
         * Time to process a single message in the flow mapper
         */
        object FlowMapperEventProcessingTime : Metric<Timer>("flow.mapper.event.processing.time", CordaMetrics::timer)

        /**
         * Count of events dropped due to deduplication of start events by the mapper.
         */
        object FlowMapperDeduplicationCount : Metric<Counter>("flow.mapper.deduplication.count", Metrics::counter)

        /**
         * Count of new states being created.
         */
        object FlowMapperCreationCount : Metric<Counter>("flow.mapper.creation.count", Metrics::counter)

        /**
         * Count of states being cleaned up.
         */
        object FlowMapperCleanupCount : Metric<Counter>("flow.mapper.cleanup.count", Metrics::counter)

        /**
         * Time taken between a mapper event being published and processed.
         */
        object FlowMapperEventLag : Metric<Timer>("flow.mapper.event.lag", CordaMetrics::timer)

        /**
         * Count of expired session events dropped by the mapper.
         */
        object FlowMapperExpiredSessionEventCount : Metric<Counter>("flow.mapper.expired.session.event.count", Metrics::counter)

        /**
         * FLOW SESSION METRICS
         *
         * The number of messages received by sessions.
         */
        object FlowSessionMessagesReceivedCount: Metric<Counter>("flow.session.messages.received.count", Metrics::counter)

        /**
         * The number of messages sent by sessions.
         */
        object FlowSessionMessagesSentCount: Metric<Counter>("flow.session.messages.sent.count", Metrics::counter)

        /**
         * P2P Metrics
         *
         * Number of outbound peer-to-peer data messages sent.
         */
        object OutboundMessageCount : Metric<Counter>("p2p.message.outbound", Metrics::counter)

        /**
         * Number of outbound peer-to-peer data messages replayed.
         */
        object OutboundMessageReplayCount : Metric<Counter>("p2p.message.outbound.replayed", Metrics::counter)

        /**
         * Time it took for an outbound peer-to-peer message to be delivered end-to-end (from initial processing to acknowledgement).
         */
        object OutboundMessageDeliveryLatency : Metric<Timer>("p2p.message.outbound.latency", CordaMetrics::timer)

        /**
         * Number of outbound peer-to-peer data messages that were discarded because their TTL expired.
         */
        object OutboundMessageTtlExpired : Metric<Counter>("p2p.message.outbound.expired", Metrics::counter)

        /**
         * Number of inbound peer-to-peer data messages received.
         */
        object InboundMessageCount : Metric<Counter>("p2p.message.inbound", Metrics::counter)

        /**
         * Number of outbound peer-to-peer sessions that timed out (indicating communication issues with peers).
         */
        object OutboundSessionTimeoutCount : Metric<Counter>("p2p.session.outbound.timeout", Metrics::counter)

        /**
         * Number of inbound peer-to-peer sessions that timed out (indicating communication issues with peers).
         */
        object InboundSessionTimeoutCount : Metric<Counter>("p2p.session.inbound.timeout", Metrics::counter)

        /**
         * Number of outbound peer-to-peer sessions.
         */
        class OutboundSessionCount(computation: Supplier<Number>): ComputedValue<Nothing>("p2p.session.outbound", computation)

        /**
         * Number of inbound peer-to-peer sessions.
         */
        class InboundSessionCount(computation: Supplier<Number>): ComputedValue<Nothing>("p2p.session.inbound", computation)

        /**
         * Time it took for an inbound request to the p2p gateway to be processed.
         */
        object InboundGatewayRequestLatency: Metric<Timer>("p2p.gateway.inbound.request.time", CordaMetrics::timer)

        /**
         * Time it took for an outbound request from the p2p gateway to be processed.
         */
        object OutboundGatewayRequestLatency: Metric<Timer>("p2p.gateway.outbound.request.time", CordaMetrics::timer)

        /**
         * Number of inbound connections established.
         */
        object InboundGatewayConnections: Metric<Counter>("p2p.gateway.inbound.tls.connections", Metrics::counter)

        /**
         * Number of outbound connections established.
         */
        object OutboundGatewayConnections: Metric<Counter>("p2p.gateway.outbound.tls.connections", Metrics::counter)

        /**
         * Time it took for gateway to process certificate revocation checks.
         */
        object GatewayRevocationChecksLatency: Metric<Timer>("p2p.gateway.cert.revocation.check.time", CordaMetrics::timer)

        /**
         * The time taken from requesting a uniqueness check to a response being received from the perspective of
         * a client (requesting) node.
         */
        object LedgerUniquenessClientRunTime : Metric<Timer>("ledger.uniqueness.client.run.time", CordaMetrics::timer)

        /**
         * The overall time for the uniqueness checker to process a batch, inclusive of all sub-batches.
         */
        object UniquenessCheckerBatchExecutionTime :
            Metric<Timer>("uniqueness.checker.batch.execution.time", CordaMetrics::timer)

        /**
         * The number of requests in a batch processed by the uniqueness checker.
         */
        object UniquenessCheckerBatchSize :
            Metric<DistributionSummary>("uniqueness.checker.batch.size", Metrics::summary)

        /**
         * The time for the uniqueness checker to process a sub-batch, i.e. a partition of a batch segregated
         * by notary virtual node holding identity.
         */
        object UniquenessCheckerSubBatchExecutionTime :
            Metric<Timer>("uniqueness.checker.subbatch.execution.time", CordaMetrics::timer)

        /**
         * The number of requests in a sub-batch processed by the uniqueness checker.
         */
        object UniquenessCheckerSubBatchSize :
            Metric<DistributionSummary>("uniqueness.checker.subbatch.size", Metrics::summary)

        /**
         * Cumulative number of requests processed by the uniqueness checker, irrespective of batch.
         */
        object UniquenessCheckerRequestCount : Metric<Counter>("uniqueness.checker.request.count", Metrics::counter)

        /**
         * The overall execution time for a (uniqueness checker) backing store session, which includes retrieving
         * uniqueness database connection details, getting a database connection, as well as all database operations
         * (both read and write) carried out within a session context.
         */
        object UniquenessBackingStoreSessionExecutionTime : Metric<Timer>(
            "uniqueness.backingstore.session.execution.time", CordaMetrics::timer
        )

        /**
         * The execution time for a transaction within the context of a backing store session, which excludes
         * retrieving uniqueness database connection details and getting a database connection. If a transaction
         * needs to be retried due to database exceptions, then the execution time covers the cumulative duration
         * of all retry attempts.
         */
        object UniquenessBackingStoreTransactionExecutionTime : Metric<Timer>(
            "uniqueness.backingstore.transaction.execution.time", CordaMetrics::timer
        )

        /**
         * Cumulative number of errors raised by the backing store when executing a transaction. This is incremented
         * regardless of whether an expected or unexpected error is raised, and is incremented on each retry.
         */
        object UniquenessBackingStoreTransactionErrorCount : Metric<Counter>(
            "uniqueness.backingstore.transaction.error.count", Metrics::counter
        )

        /**
         * The number of attempts that were made by the backing store before a transaction ultimately succeeded. In
         * the event that a transaction was unsuccessful due to reaching the maximum number of attempts, this metric
         * is not updated.
         */
        object UniquenessBackingStoreTransactionAttempts : Metric<DistributionSummary>(
            "uniqueness.backingstore.transaction.attempts", Metrics::summary
        )

        /**
         * The time taken by the backing store to commit a transaction (i.e. write) to the database. Only updated if
         * data is written to the database, so is not cumulative across retry attempts for a given transaction.
         */
        object UniquenessBackingStoreDbCommitTime :
            Metric<Timer>("uniqueness.backingstore.db.commit.time", CordaMetrics::timer)

        /**
         * The time taken by the backing store to perform a single read operation from the database.
         */
        object UniquenessBackingStoreDbReadTime: Metric<Timer>("uniqueness.backingstore.db.read.time", CordaMetrics::timer)


        object TokenSelectionExecutionTime : Metric<Timer>("token.selection.execution.time", CordaMetrics::timer)
        object TokenSelectionDbExecutionTime : Metric<Timer>("token.selection.db.execution.time", CordaMetrics::timer)
        object TokenSelectionEmCreationTime : Metric<Timer>("token.selection.em.creation.time", CordaMetrics::timer)

        object Crypto {
            private const val PREFIX = "crypto"

            /**
             * The time taken by crypto flow operations.
             */
            object FlowOpsProcessorExecutionTime: Metric<Timer>("$PREFIX.flow.processor.execution.time", CordaMetrics::timer)

            /**
             * The time taken by crypto operations invoked by RPC message pattern requests.
             */
            object OpsProcessorExecutionTime: Metric<Timer>("$PREFIX.processor.execution.time", CordaMetrics::timer)

            /**
             * The time taken for wrapping key creation in crypto operations.
             */
            object WrappingKeyCreationTimer: Metric<Timer>("$PREFIX.wrapping.key.creation.time", CordaMetrics::timer)

            /**
             * The time taken to create entity manager factories.
             */
            object EntityManagerFactoryCreationTimer: Metric<Timer>("entity.manager.factory.creation.time", CordaMetrics::timer)

            /**
             * The time taken for crypto signing.
             */
            object SignTimer: Metric<Timer>("$PREFIX.sign.time", CordaMetrics::timer)

            /**
             * The time taken for crypto signing key lookup.
             */
            object SigningKeyLookupTimer: Metric<Timer>("$PREFIX.signing.key.lookup.time", CordaMetrics::timer)

            /**
             * The time taken to get crypto signing repository instances.
             */
            object SigningRepositoryGetInstanceTimer: Metric<Timer>("$PREFIX.signing.repository.get.instance.time", CordaMetrics::timer)

            /**
             * The time taken for crypto service sign operation.
             */
            object GetOwnedKeyRecordTimer: Metric<Timer>("$PREFIX.get.owned.key.record.time", CordaMetrics::timer)

            /**
             * The time taken for crypto cipher scheme operations.
             */
            object CipherSchemeTimer: Metric<Timer>("$PREFIX.cipher.scheme.time", CordaMetrics::timer)

            /**
             * The time taken for crypto signature spec operations.
             */
            object SignatureSpecTimer: Metric<Timer>("$PREFIX.signature.spec.time", CordaMetrics::timer)

            /**
             * The time taken for rewrapping keys in key rotation
             */
            object RewrapKeysTimer: Metric<Timer>("$PREFIX.rewrap.time", CordaMetrics::timer)

        }

        object Membership {
            private const val PREFIX = "membership"

            /**
             * Time taken for a membership persistence transaction to complete.
             */
            object PersistenceTransactionExecutionTime: Metric<Timer>(
                "$PREFIX.persistence.transaction.time",
                CordaMetrics::timer
            )

            /**
             * Total time taken for a membership persistence handler to execute.
             */
            object PersistenceHandlerExecutionTime: Metric<Timer>(
                "$PREFIX.persistence.handler.time",
                CordaMetrics::timer
            )

            /**
             * Time taken by each stage of network registration.
             */
            object RegistrationHandlerExecutionTime: Metric<Timer>(
                "$PREFIX.registration.handler.time",
                CordaMetrics::timer
            )

            /**
             * Time taken by each membership actions handler (e.g. distribute network data).
             */
            object ActionsHandlerExecutionTime: Metric<Timer>(
                "$PREFIX.actions.handler.time",
                CordaMetrics::timer
            )

            /**
             * Time taken to execute each stage of network synchronisation between members and the MGM.
             */
            object SyncHandlerExecutionTime: Metric<Timer>(
                "$PREFIX.sync.handler.time",
                CordaMetrics::timer
            )

            /**
             * Metric to capture the changes in group size.
             */
            class MemberListCacheSize<T : List<*>>(list: T?): ComputedValue<T>(
                "$PREFIX.memberlist.cache.size",
                list,
                Collection<*>::doubleSize
            )
        }

        /**
         * The time taken by crypto operations from the flow side.
         */
        object CryptoOperationsFlowTime: Metric<Timer>("flow.crypto.time", CordaMetrics::timer)

        object Ledger {

            /**
             * The time taken by transaction verification from the flow side.
             */
            object TransactionVerificationFlowTime : Metric<Timer>("ledger.flow.verification.time", CordaMetrics::timer)

            /**
             * The time taken by verification processor to verify a ledger transaction.
             */
            object TransactionVerificationTime: Metric<Timer>("ledger.verification.time", CordaMetrics::timer)

            /**
             * The time taken by contract verification when verifying a transaction.
             */
            object ContractVerificationTime : Metric<Timer>("ledger.verification.contract.total.time", CordaMetrics::timer)

            /**
             * The time taken per contract by contract verification when verifying a transaction.
             */
            object ContractVerificationContractTime : Metric<Timer>("ledger.verification.contract.time", CordaMetrics::timer)

            /**
             * The number of executed contracts during contract verification when verifying a transaction.
             */
            object ContractVerificationContractCount : Metric<DistributionSummary>(
                "ledger.verification.contract.count",
                Metrics::summary
            )

            /**
             * The time taken by ledger persistence operations from the flow side.
             */
            object PersistenceFlowTime : Metric<Timer>("ledger.flow.persistence.time", CordaMetrics::timer)

            /**
             * The time taken by ledger persistence processor to perform persistence operation.
             */
            object PersistenceExecutionTime: Metric<Timer>("ledger.persistence.time", CordaMetrics::timer)

            object PersistenceTxExecutionTime: Metric<Timer>("ledger.persistence.tx.time", CordaMetrics::timer)

            /**
             * The length of resolved backchains when performing backchain resolution.
             *
             * - 0.05 included to get a sense of the smallest chains.
             * - 0.50 included for average chain lengths.
             * - 0.95, 0.99 for large chain lengths.
             * - 1.00 for outlier chain lengths.
             */
            object BackchainResolutionChainLength : Metric<DistributionSummary>(
                "ledger.backchain.resolution.chain.length",
                { name, tags ->
                    DistributionSummary.builder(name)
                        .publishPercentiles(0.05, 0.50, 0.95, 0.99, 1.00)
                        .publishPercentileHistogram()
                        .tags(tags)
                        .register(registry)
                }
            )

            /**
             * The time taken from requesting a uniqueness check to a response being received from the perspective of
             * a client (requesting) node.
             */
            object UniquenessClientRunTime : Metric<Timer>("ledger.uniqueness.client.run.time", CordaMetrics::timer)
        }

        object Serialization {

            /**
             * The time taken serializing an object.
             */
            object SerializationTime : Metric<Timer>("serialization.amqp.serialization.time", CordaMetrics::timer)

            /**
             * The time taken deserializing an object.
             */
            object DeserializationTime : Metric<Timer>("serialization.amqp.deserialization.time", CordaMetrics::timer)
        }

        /**
         * A [Gauge] metric that computes its value using a lambda.
         */
        sealed class ComputedValue<T> private constructor(name: String, meter: (String, Iterable<micrometerTag>) -> Gauge)
            : Metric<Gauge>(name, meter) {

            /**
             * Creates a [Gauge] for the value of [computation]. The [MeterRegistry] will hold
             * a strong reference to [computation], and hence to any objects it also uses.
             *
             * @param name A unique name for this [Gauge].
             * @param computation A lambda to compute our current value.
             */
            constructor(name: String, computation: Supplier<Number>) : this(name, meter = { n, tags ->
                Gauge.builder(n, computation)
                    .tags(tags)
                    .register(registry)
            })

            /**
             * Create a [Gauge] for a property of [weakObj].
             *
             * @param name A unique name for this [Gauge].
             * @param weakObj A weakly referenced object for applying [computation].
             * @param computation A lambda to compute our current value using [weakObj].
             */
            constructor(name: String, weakObj: T?, computation: ToDoubleFunction<T>) : this(name, meter = { n, tags ->
                Gauge.builder(n, weakObj, computation)
                    .tags(tags)
                    .register(registry)
            })
        }

        sealed class DiskSpace(private val name: String, private val path: Path) {
            sealed class Value(
                name: String,
                description: String,
                path: Path,
                computation: ToLongFunction<File>
            ): Metric<Gauge>(name, meter = { n, tags ->
                if (path.fileSystem == FileSystems.getDefault()) {
                    val file = path.toFile()
                    Gauge.builder(n, file) { f -> computation.applyAsLong(f).toDouble() }
                        .tags(Tags.concat(tags, "path", file.absolutePath))
                        .description(description)
                        .baseUnit(BaseUnits.BYTES)
                        .strongReference(true)
                        .register(registry)
                } else {
                    // The filesystem does not support Path.toFile()
                    VoidGauge
                }
            })

            inner class TotalSpace: Value("${name}.disk.total", "Total space for path", path, File::getTotalSpace)
            inner class UsableSpace: Value("${name}.disk.free", "Usable space for path", path, File::getUsableSpace)

            fun metrics(): List<Metric<Gauge>> {
                return listOf(TotalSpace(), UsableSpace())
            }

            /**
             * Disk space used to store CPKs and their chunks.
             */
            class Cpks(path: Path): DiskSpace("cpks", path)

            /**
             * Disk space used to unpack CPKs.
             */
            class UnpackedCpks(path: Path): DiskSpace("cpks.unpacked", path)
        }

        object Db {

            /**
             * Metric for the time taken to process an entity persistence request, from the moment the request is received from Kafka.
             */
            object EntityPersistenceRequestTime : Metric<Timer>("db.entity.persistence.request.time", CordaMetrics::timer)

            /**
             * Metric for the lag between the flow putting the entity persistence request to Kafka and the EntityMessageProcessor.
             */
            object EntityPersistenceRequestLag : Metric<Timer>("db.entity.persistence.request.lag", CordaMetrics::timer)

            /**
             * Metric for the time taken for a full reconciliation run.
             */
            object ReconciliationRunTime : Metric<Timer>("db.reconciliation.run.time", CordaMetrics::timer)

            /**
             * Metric for the number of reconciled records for a reconciliation run.
             */
            object ReconciliationRecordsCount : Metric<DistributionSummary>("db.reconciliation.records.count", Metrics::summary)
        }

        object Messaging {

            /**
             * Time it took to execute a message pattern processor
             */
            object MessageProcessorTime : Metric<Timer>("messaging.processor.time", CordaMetrics::timer)

            /**
             * The size of batches of messages received in polls from the message bus by consumers.
             */
            object ConsumerBatchSize : Metric<DistributionSummary>("consumer.batch.size", Metrics::summary)

            /**
             * The time taken to commit a processed batch of messages back to the bus.
             */
            object MessageCommitTime : Metric<Timer>("messaging.commit.time", CordaMetrics::timer)

            /**
             * Generic consumer poll time, time taken by kafka to respond to consumer polls for each client ID.
             */
            object ConsumerPollTime : Metric<Timer>("consumer.poll.time", CordaMetrics::timer)

            /**
             * Measure for the number of chunks generated when writing records.
             */
            object ProducerChunksGenerated : Metric<DistributionSummary>("producer.chunks.generated", Metrics::summary)

            /**
             * Measure for the number of in-memory states held in compacted consumers.
             */
            class CompactedConsumerInMemoryStore(computation: Supplier<Number>) : ComputedValue<Nothing>(
                "consumer.compacted.inmemory.store",
                computation
            )

            /**
             * Measure for the number of in-memory states held in consumers with partitions.
             */
            class PartitionedConsumerInMemoryStore(computation: Supplier<Number>) : ComputedValue<Nothing>(
                "consumer.partitioned.inmemory.store",
                computation
            )

            /**
             * Record how long a HTTP RPC call from the messaging library takes to receive a response
             */
            object HTTPRPCResponseTime : Metric<Timer>("rpc.http.response.time", CordaMetrics::timer)

            /**
             * Record time needed to process a RPC request
             */
            object RpcServerResponseTime : Metric<Timer>("rpc.server.response.time", CordaMetrics::timer)
            object RpcServerDeserializeTime : Metric<Timer>("rpc.server.deserialize.time", CordaMetrics::timer)
            object RpcServerSerializeTime : Metric<Timer>("rpc.server.serialize.time", CordaMetrics::timer)
            object RpcServerProcessTime : Metric<Timer>("rpc.server.process.time", CordaMetrics::timer)

            /**
             * Record the size of HTTP RPC responses
             */
            object HTTPRPCResponseSize : Metric<DistributionSummary>("rpc.http.response.size", Metrics::summary)

            /**
             * Record how long a HTTP RPC call from the messaging library takes to process on the server side
             */
            object HTTPRPCProcessingTime : Metric<Timer>("rpc.http.processing.time", CordaMetrics::timer)
        }

        object TaskManager {
            /**
             * Time it took to execute a task, includes time waiting to be scheduled.
             */
            object TaskCompletionTime : Metric<Timer>("taskmanager.completion.time", CordaMetrics::timer)

            /**
             * The number of live tasks running or scheduled in the task manager.
             */
            class LiveTasks(computation: Supplier<Number>) : ComputedValue<Nothing>("taskmanager.live.tasks", computation)
        }

        object StateManger {
            private const val PREFIX = "state.manager"

            /**
             * Time taken to execute a specific State Manager operation.
             */
            object ExecutionTime : Metric<Timer>("$PREFIX.execution.time", CordaMetrics::timer)
        }
    }

    /**
     * Metric tag names
     *
     * NOTE: please ensure the metric names adhere to the convensions described on
     * https://micrometer.io/docs/concepts#_naming_meters
     */
    enum class Tag(val value: String) {
        /**
         * URI's path for which the metric is applicable.
         */
        UriPath("uri.path"),

        /**
         * Http method for which the metric is applicable.
         */
        HttpMethod("http.method"),

        /**
         * The URI that a HTTP request was sent to
         */
        HttpRequestUri("http.request.uri"),

        /**
         * Response code received for a HTTP response
         */
        HttpResponseCode("http.response.code"),

        /**
         * Type of the SandboxGroup to which the metric applies.
         */
        SandboxGroupType("sandbox.type"),

        /**
         * Source of metric.
         */
        WorkerType("worker.type"),

        /**
         * Virtual Node for which the metric is applicable.
         */
        VirtualNode("virtualnode"),

        /**
         * Message pattern type for which the metric is applicable.
         */
        MessagePatternType("messagepattern.type"),

        /**
         * The name of the operation performed
         */
        OperationName("operation.name"),

        /**
         * Message pattern clientId for which the metric is applicable.
         */
        MessagePatternClientId("messagepattern.clientid"),

        /**
         * Flow class for which the metric is applicable.
         */
        FlowClass("flow.class"),

        /**
         * Flow class for which the metric is applicable.
         */
        FlowType("flow.type"),

        /**
         * The flow suspension action this metric was recorded for.
         */
        FlowSuspensionAction("flow.suspension.action"),

        /**
         * The flow event type this metric was recorded for.
         */
        FlowEvent("flow.event"),

        /**
         * The status of the operation. Can be used to indicate whether an operation was successful or failed.
         */
        OperationStatus("status"),

        /**
         * The source virtual node in peer-to-peer communication.
         */
        SourceVirtualNode("virtualnode.source"),

        /**
         * The destination virtual node in peer-to-peer communication.
         */
        DestinationVirtualNode("virtualnode.destination"),

        /**
         * The ledger type.
         */
        LedgerType("ledger.type"),

        /**
         * The contract class name of a contract being executed.
         */
        LedgerContractName("ledger.contract.name"),

        /**
         * The class being serialized to or deserialized from.
         */
        SerializedClass("serialized.class"),

        /**
         * The membership group within which peer-to-peer communication happens.
         */
        MembershipGroup("group"),

        /**
         * The type of a peer-to-peer message.
         */
        MessageType("message.type"),

        /**
         * The subsystem that sends or receives a peer-to-peer message from the network layer.
         */
        MessagingSubsystem("subsystem"),

        /**
         * Type of result returned. Currently used by uniqueness client and checker to indicate
         * successful vs failed results.
         */
        ResultType("result.type"),

        /**
         * Method to lookup signing keys. Currently used by SingingRepositoryImpl to indicate whether the lookup is via public key hashes or
         * public key short hashes.
         */
        SigningKeyLookupMethod("lookup.method"),

        /**
         * Label to identify the method inside a class / implementation.
         */
        PublicKeyType("publickey.type"),

        /**
         * Identifies the signature signing scheme name to create signatures during crypto signing operations.
         */
        SignatureSpec("signature.spec"),

        /**
         * Task manager name.
         */
        TaskManagerName("task.manager.name"),

        /**
         * Task type.
         */
        TaskType("task.type"),

        /**
         * Identifier of a tenant either a virtual node identifier or cluster level tenant id.
         */
        Tenant("tenant"),

        /**
         * Boolean value indicating whether the metric relates to a duplicate request. Used by the
         * uniqueness checker to indicate when a request has been seen before and the original
         * result is being returned.
         */
        IsDuplicate("duplicate"),

        /**
         * Type of error raised in failure cases
         */
        ErrorType("error.type"),

        /**
         * Source endpoint of a peer-to-peer message or connection.
         */
        SourceEndpoint("endpoint.source"),

        /**
         * Destination endpoint of a peer-to-peer message or connection.
         */
        DestinationEndpoint("endpoint.destination"),

        /**
         * Response type (e.g. status code) of an HTTP request.
         */
        HttpResponseType("response.type"),

        /**
         * Result of a TLS connection (i.e. success or failure).
         */
        ConnectionResult("connection.result"),

        /**
         * Name of a message bus topic published to or consumed from.
         */
        Topic("topic"),

        /**
         * Partition of a message bus topic published to or consumed from.
         */
        Partition("partition"),

        /**
         * Type of event received by the token selection processor.
         */
        TokenSelectionEvent("token.selection.event"),

        /**
         * Token selection database operation.
         */
        TokenSelectionDbOperation("token.selection.db.operation")
    }

    /**
     * Prometheus requires the same set of tags to be populated for a specific metric name.
     * Otherwise, metrics will be (silently) not exported.
     *
     * This value can be used to comply with this rule in scenarios where a tag is not relevant in some conditions,
     * but it still needs to be populated in order to avoid lost data points.
     */
    const val NOT_APPLICABLE_TAG_VALUE = "not_applicable"

    val registry: CompositeMeterRegistry = Metrics.globalRegistry

    /**
     * Configure the Metrics Registry
     *
     * @param workerType Type of Worker, will be tagged to each metric.
     * @param registry Registry instance
     */
    fun configure(workerType: String, registry: MeterRegistry) {
        this.registry.add(registry).config()
            .commonTags(Tag.WorkerType.value, workerType)
            .meterFilter(object : MeterFilter {
                override fun map(id: Meter.Id): Meter.Id {
                    // prefix all metrics with `corda`, except standard JVM and Process metrics
                    @Suppress("ComplexCondition")
                    return if (
                        id.name.startsWith("corda") ||
                        id.name.startsWith("jvm") ||
                        id.name.startsWith("system") ||
                        id.name.startsWith("process")
                    ) {
                        id
                    } else {
                        id.withName("corda." + id.name)
                    }
                }
            })
    }

    private fun timer(name: String, tags: Iterable<micrometerTag>): Timer {
        return Timer.builder(name)
            .publishPercentileHistogram()
            .tags(tags)
            .register(registry)
    }

    class MeterBuilder<T : Meter>(
        val name: String,
        val func: (String, Iterable<micrometerTag>) -> T
    ) {
        val allTags: MutableList<micrometerTag> = mutableListOf()

        /**
         * Remove all tags from this builder.
         */
        fun resetTags(): MeterBuilder<T> {
            allTags.clear()
            return this
        }

        /**
         * Tag the metric with the Holding ID short hash for the Virtual Node.
         */
        fun forVirtualNode(holdingId: String): MeterBuilder<T> {
            return withTag(Tag.VirtualNode, holdingId)
        }

        fun withTag(key: Tag, value: String): MeterBuilder<T> {
            allTags.add(micrometerTag.of(key.value, value))
            return this
        }

        fun build(): T {
            return func(name, allTags)
        }

        // A Meter is uniquely identified by just its name and tags.
        fun buildPreFilterId(): Meter.Id {
            return Meter.Id(name, Tags.of(allTags), null, null, Meter.Type.OTHER)
        }
    }
}

private val Collection<*>.doubleSize: Double
    get() = size.toDouble()

/**
 * This is a dummy "placeholder" gauge.
 */
private object VoidGauge : NoopGauge(Meter.Id("", Tags.empty(), null, null, Meter.Type.GAUGE))
