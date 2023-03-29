package net.corda.messagebus.api.admin

/**
 * [Admin] provides an API for admin operations on the message bus
 */
interface Admin {

    /**
     * Returns a list of all topics supported by the message bus.
     */
    fun getTopics():Set<String>
}
