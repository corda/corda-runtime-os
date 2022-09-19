package net.corda.persistence.common

/**
 *  Keys to look up the per entity sandbox objects.
 */
object EntitySandboxContextTypes {
    const val SANDBOX_SERIALIZER = "AMQP_SERIALIZER"
    const val SANDBOX_EMF = "ENTITY_MANAGER_FACTORY"
}
