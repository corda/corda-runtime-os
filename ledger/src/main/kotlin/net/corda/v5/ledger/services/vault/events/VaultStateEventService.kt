package net.corda.v5.ledger.services.vault.events

import net.corda.v5.application.injection.CordaServiceInjectable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.stream.DurableCursor
import net.corda.v5.base.stream.CursorException
import net.corda.v5.ledger.contracts.ContractState
import java.util.function.BiConsumer

/**
 * [VaultStateEventService] allows [CordaService]s to subscribe to vault state events triggered by the recording of [ContractState]s to the
 * vault.
 */
@DoNotImplement
interface VaultStateEventService : CordaServiceInjectable {

    /**
     * Subscribe to vault events and execute the given [function] using each [VaultStateEvent]. These events are emitted when states are
     * produced or consumed (saved to the vault as output or input states).
     *
     * Subscription using this method is reliable, meaning all events will eventually be processed even if the process crashes. To achieve
     * this behaviour, this method must be executed when the Corda process is restarted. The consistent lifecycle of [CordaService]s
     * provides a safe location to execute [subscribe].
     *
     * This overload of [subscribe] provides a fully managed solution, that moves the position of processed [VaultStateEvent]s after each
     * event is processed. For more control over the position of processed events, see the overload of [subscribe] that returns a
     * [DurableCursor].
     *
     * Uncaught exceptions thrown within the provided [function] are caught within the platform code. When this happens the subscriber will
     * continue onto the next event and update its position, meaning that the event will never be processed again by the subscriber.
     *
     * Example usage within a [CordaService]:
     *
     * - Kotlin:
     *
     * ```kotlin
     * class LoggingVaultEventSubscriber : CordaService {
     *
     *     @CordaInjectPreStart
     *     private lateinit var vaultEventService: VaultEventService
     *
     *     override fun onEvent(event: ServiceEvent) {
     *         if (event == ServiceLifecycleEvent.SERVICE_START) {
     *             vaultEventService.subscribe("Logging vault event subscriber") { deduplicationId, vaultEvent ->
     *                 log.info("Processing value: $vaultEvent with deduplication id: $deduplicationId")
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * - Java:
     *
     * ```java
     * public class LoggingVaultEventSubscriber implements CordaService {
     *
     *     @CordaInjectPreStart
     *     private VaultEventService vaultEventService;
     *
     *     @Override
     *     public void onEvent(@NotNull ServiceLifecycleEvent event) {
     *         if (event == ServiceLifecycleEvent.SERVICE_START) {
     *             vaultEventService.subscribe("Logging vault event subscriber", (deduplicationId, vaultEvent) -> {
     *                 log.info("Processing value: " + vaultEvent + " with deduplication id: " + deduplicationId);
     *             });
     *         }
     *     }
     * }
     * ```
     *
     * [ServiceLifecycleEvent.STATE_MACHINE_STARTED] should be used instead of [ServiceLifecycleEvent.SERVICE_START] if flows are being
     * started from within [subscribe]'s [function].
     *
     * @param name The name of the subscriber. This name __must be unique__ per subscriber and cursor as it used to maintain the position of
     * processed events.
     * @param function The [BiConsumer] that is executed for each [VaultStateEvent] output from the vault. The provided [Long] value is a
     * deduplication id to use for deduplication purposes.
     *
     * @throws CordaRuntimeException If the provided [name] already belongs to another cursor or subscriber.
     *
     * @see subscribe The overload of [subscribe] provides more control over the processing of events due to it returning a
     * [DurableCursor].
     */
    fun subscribe(name: String, function: BiConsumer<Long, VaultStateEvent<ContractState>>)

    /**
     * Subscribe to vault events and receive a [DurableCursor] that polls for [VaultStateEvent]s emitted by the vault which can then be
     * processed. These events are emitted when states are produced or consumed (saved to the vault as output or input states).
     *
     * Subscription using this method provides full control over the retrieval and processing of events. Events must be polled and the
     * cursor's position must be maintained and committed to ensure that events are not reprocessed.
     *
     * To achieve reliable behaviour, this method must be executed when the Corda process is restarted, returning a new cursor to continue
     * processing from the last committed position. The consistent lifecycle of [CordaService]s provides a safe location to execute
     * [subscribe].
     *
     * This overload of [subscribe] provides a self managed solution which is handled by interacting with the [DurableCursor]. For a
     * simpler and fully managed solution, see the overload of [subscribe] that receives a `function`/`callback` as input.
     *
     * Example usage within a [CordaService]:
     *
     * - Kotlin:
     *
     * ```kotlin
     * class LoggingVaultEventSubscriber : CordaService {
     *
     *     @CordaInjectPreStart
     *     private lateinit var vaultEventService: VaultEventService
     *
     *     private lateinit var thread: Thread
     *
     *     override fun onEvent(event: ServiceLifecycleEvent) {
     *         if (event == ServiceLifecycleEvent.SERVICE_START) {
     *             val cursor: VaultEventCursor = vaultEventService.subscribe(Logging vault event subscriber")
     *             thread = thread(name = Logging vault event subscriber", isDaemon = true) {
     *                 while (!thread.isInterrupted) {
     *                     val result = cursor.poll(50, 5.minutes);
     *                     if (!result.isEmpty) {
     *                         for (positionedValue in result.positionedValues) {
     *                             log.info("Processing value: ${positionedValue.value} at position: ${positionedValue.position}")
     *                         }
     *                         cursor.commit(result.lastPosition)
     *                     }
     *                 }
     *             }
     *         }
     *     }
     * }
     * ```
     *
     * - Java:
     *
     * ```java
     * public class LoggingVaultEventSubscriber implements CordaService {
     *
     *     @CordaInjectPreStart
     *     private VaultEventService vaultEventService;
     *
     *     private Thread thread;
     *
     *     @Override
     *     public void onEvent(@NotNull ServiceLifecycleEvent event) {
     *         if (event == ServiceLifecycleEvent.SERVICE_START) {
     *             VaultEventCursor cursor = vaultEventService.subscribe("Logging vault event subscriber");
     *             ThreadGroup threadGroup = new ThreadGroup("Logging vault event subscriber");
     *             threadGroup.setDaemon(true);
     *             thread = new Thread(threadGroup, () -> {
     *                 while (!thread.isInterrupted()) {
     *                     PollResult<VaultEvent> result = cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
     *                     if (!result.isEmpty()) {
     *                         for (PositionedValue<VaultEvent> positionedValue : result.getPositionedValues()) {
     *                             log.info("Processing value: " + positionedValue.getValue() + " at position: " + positionedValue.getPosition());
     *                         }
     *                         cursor.commit(result.getLastPosition());
     *                     }
     *                 }
     *             });
     *             thread.start();
     *         }
     *     }
     * }
     * ```
     *
     * [ServiceLifecycleEvent.STATE_MACHINE_STARTED] should be used instead of [ServiceLifecycleEvent.SERVICE_START] if flows are being
     * started when processing polled events.
     *
     * @param name The name of the cursor. This name __must be unique__ per subscriber and cursor as it used to maintain the position of
     * processed events.
     *
     * @throws CordaRuntimeException If the provided [name] already belongs to another cursor or subscriber.
     *
     * @throws CursorException If the [DurableCursor] fails to poll ([DurableCursor.poll]) events, commit ([DurableCursor.commit]) and
     * retrieve ([DurableCursor.currentPosition]] its position.
     *
     * @see subscribe The overload of [subscribe] provides fully managed processing of events.
     */
    fun subscribe(name: String): DurableCursor<VaultStateEvent<ContractState>>
}