package net.corda.application.dbsetup

/**
 * The implementation of this interface is called during the Corda runtime
 * set up.
 */
interface DbSetup {
    fun run()
}
