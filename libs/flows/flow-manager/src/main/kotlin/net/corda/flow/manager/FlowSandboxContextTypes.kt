package net.corda.flow.manager

/**
 * Defines the keys for storing flow services in the sandbox context
 */
object FlowSandboxContextTypes {
    const val DEPENDENCY_INJECTOR = "DEPENDENCY_INJECTOR"
    const val CHECKPOINT_SERIALIZER = "CHECKPOINT_SERIALIZER"
}
