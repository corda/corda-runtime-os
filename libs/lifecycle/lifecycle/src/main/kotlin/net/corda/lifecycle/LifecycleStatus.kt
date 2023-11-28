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
     * The component is not yet setup or is temporarily non-functional (e.g. reconfiguring).
     */
    DOWN,

    /**
     * The component has encountered an error. This could be set by the user, but it will also be set if the coordinator
     * encounters an unhandled error.
     */
    ERROR,

    /**
     * The component is running normally and all setup has been completed.
     */
    UP,
}
