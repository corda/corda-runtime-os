package net.corda.virtualnode.manager.api

import net.corda.sandbox.SandboxGroup

/**
 * Prior to executing or resuming a flow, we need to register any custom classes with the Corda runtime libraries.
 * This interface provides the two methods to do that.
 *
 * The virtual node manager will enumerate for services that implement this interface, then before executing a flow
 *
 *     registrations.forEach { it.register(sandboxGroup) }
 *
 *  and when we remove a cordapp/CPI from a running process:
 *
 *     registrations.forEach { it.unregister(sandboxGroup) }
 *
 *  NB. this is "version 1" for the crypto PoC, and is likely to change.
 */
interface RuntimeRegistration {
    /**
     * Any implementation of this should "register" some aspect of the cpi / [SandboxGroup] with
     * some part of the corda runtime.
     */
    fun register(sandboxGroup: SandboxGroup)

    /**
     * A cordapp/CPI can potentially be unloaded by the system, so we need to implement this function.
     */
    fun unregister(sandboxGroup: SandboxGroup)
}
