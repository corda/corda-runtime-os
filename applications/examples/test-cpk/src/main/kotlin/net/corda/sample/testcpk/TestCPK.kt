package net.corda.sample.testcpk

//import org.slf4j.LoggerFactory

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