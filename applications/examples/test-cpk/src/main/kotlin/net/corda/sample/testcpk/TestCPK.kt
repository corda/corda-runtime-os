package net.corda.sample.testcpk

/**
 * Dummy CordApp used in the `:applications:examples:test-sandbox`.
 */
class TestCPK: Runnable {

    init {
        println("net.corda.sample.testcpk.TestCPK INIT.")
    }


    override fun run() {
        println("net.corda.sample.testcpk.TestCPK RUN.")
    }
}