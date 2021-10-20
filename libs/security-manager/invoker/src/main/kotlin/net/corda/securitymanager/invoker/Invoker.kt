package net.corda.securitymanager.invoker

/** Used to execute actions within the context of the bundle containing the implementation of this interface. */
interface Invoker {
    /** Executes the provided lambda. */
    fun performAction(lambda: () -> Unit)
}