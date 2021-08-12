package net.corda.lifecycle

/**
 * The current state of a lifecycle component.
 *
 * Changes in this state are client driven. The lifecycle infrastructure uses these states to signal between components
 * when their dependents go up or down. This is used to implement domino logic, where a parent component moves to up
 * only if its children are all up.
 */
enum class LifecycleStatus {
    /**
     * The component is running normally and all setup has been completed.
     */
    UP,

    /**
     * The component is not yet setup, encountered an error, or is temporarily non-functional (e.g. reconfiguring).
     */
    DOWN
}