package net.corda.flow.manager

/**
 * Defines the keys for storing flow services in the sandbox context
 */
object FlowSandboxContextTypes {
    const val DEPENDENCY_INJECTOR = "DEPENDENCY_INJECTOR"
    const val CHECKPOINT_SERIALIZER = "CHECKPOINT_SERIALIZER"
    const val AMQP_P2P_SERIALIZATION_SERVICE = "AMQP_SERIALIZER"
    const val INITIATING_TO_INITIATED_FLOWS = "INITIATING_TO_INITIATED_FLOWS"
}
