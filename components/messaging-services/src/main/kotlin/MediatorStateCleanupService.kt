import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/**
 * Generic Messaging Services for cleaning up terminated MediatorState objects from the State Manager.
 *
 * A terminated state is one which the message processor has returned a value of null back to the mediator for that state.
 * The wrapper MediatorState is held onto for a period longer to retain the outputs of the message processor so it can
 * be replayed in the event of failures.
 *
 * This service is responsible for setting up the scheduled task job,
 * as well the job to execute cleanup of the the states.
 */
interface MediatorStateCleanupService : Lifecycle {

    /**
     * Trigger (re)creation of the cleanup subscriptions in response to [config] being received.
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}

