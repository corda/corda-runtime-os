package net.corda.ledger.notary.worker.selection.impl

import net.corda.ledger.notary.worker.selection.NotaryWorkerSelectorService
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.ledger.common.Party
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope
import java.lang.IndexOutOfBoundsException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implements round-robin selection of notary workers per flow.
 */
@Component(
    service = [ UsedByFlow::class ],
    scope = ServiceScope.PROTOTYPE
)
class NotaryWorkerSelectorServiceImpl @Activate constructor():
    NotaryWorkerSelectorService, SingletonSerializeAsToken, UsedByFlow {

    private val selection = AtomicInteger(0)

    /**
     * This function will do a round-robin selection on the given [list].
     * The selections persist between flows, throughout the entire lifecycle of the application.
     *
     * In case the list changes over time, and we are over- or under-indexing it will reset the selection to 0.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun next(list: List<Party>): Party {
        return try {
            list[selection.getAndUpdate { (it + 1) % list.size }]
        } catch (e: IndexOutOfBoundsException) {
            selection.set(0)
            list[0]
        }
    }
}