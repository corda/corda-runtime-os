package net.corda.sample.testcpk

/**
 * Dummy CordApp used in the `:applications:examples:test-sandbox`.
 */
class TestCPK: Runnable {

    init {
        println("TestCPK INIT.")
    }


    override fun run() {
        println("TestCPK RUN.")
    }
}