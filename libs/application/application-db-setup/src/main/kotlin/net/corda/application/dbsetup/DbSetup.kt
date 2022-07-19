package net.corda.application.dbsetup

/**
 * The implementation of this interface is called during the Corda runtime
 * set up.
 */
interface DbSetup {
    fun run()

    // TODO - this will probably want hoisting into its own interface as it will be called during "db like" component start up.
    /**
     * Initialize jdbc
     *
     * @throws ClassNotFoundException if driver can be initialized
     * @throws IllegalArgumentException if no drivers are loaded
     */
    fun initializeJdbc(driverName: String)
}
