package net.corda.p2p.linkmanager.tracker

import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.delivery.ReplayScheduler
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.virtualnode.toCorda
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

internal class AckProcessor(
    private val partitionsStates: PartitionsStates,
    private val cache: DataMessageCache,
    private val replayScheduler: ReplayScheduler<SessionManager.Counterparties, String>,
) : PubSubProcessor<String, String> {

    override fun onNext(event: Record<String, String>): Future<Unit> {
        val future = CompletableFuture.completedFuture(Unit)
        val messageId = event.key
        val messageRecord = cache.remove(messageId) ?: return future
        partitionsStates.forget(messageRecord)
        val counterparties = SessionManager.Counterparties(
            ourId = messageRecord.message.header.source.toCorda(),
            counterpartyId = messageRecord.message.header.destination.toCorda(),
        )
        replayScheduler.removeFromReplay(
            messageId,
            counterparties,
        )
        return CompletableFuture.completedFuture(Unit)
    }

    override val keyClass = String::class.java
    override val valueClass = String::class.java
}
