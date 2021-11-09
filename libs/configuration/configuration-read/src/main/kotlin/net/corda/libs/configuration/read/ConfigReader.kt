package net.corda.libs.configuration.read

import net.corda.lifecycle.Lifecycle


/**
 * Provides a mechanism for receiving configuration data from a backend (e.g. message bus, file, etc.)
 *
 * All callbacks registered will receive a snapshot of all config followed by further updates.  This applies
 * even if the service is currently running.  If the configuration isn't yet available you will receive the
 * snapshot (and subsequent updates) as soon as it is.
 *
 * As this interface implements a [Lifecycle] all registrations will remain in effect even if a [stop] is called.
 * Therefore, it is possible for the [ConfigReader] to [stop] and then [start] and the callbacks will again
 * receive a snapshot of the configuration followed by any updates.
 *
 * However, calling [close] on the [ConfigReader] will cause it to drop all callback registrations.  With the
 * [Lifecycle] it is never expected that a closed object will be able to continue.
 */
interface ConfigReader : Lifecycle {
    /**
     * Register a callback for any configuration changes
     * If the service is already running, you will receive a snapshot of all available configurations
     */
    fun registerCallback(configListener: ConfigListener): AutoCloseable
}
