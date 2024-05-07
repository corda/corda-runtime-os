package net.corda.blobinspector

import org.junit.jupiter.api.Test

class AppTest {
    @Test
    fun parseCashSignedTransaction() {
        run("cash-stx-db.blob")
    }

    @Test
    fun parseCashWireTransaction() {
        run("cash-wtx.blob")
    }

    @Test
    fun parseNetworkParameters() {
        run("network-parameters")
    }

    @Test
    fun parseNetworkNodeInfo() {
        run("node-info")
    }

    @Test
    fun parseOlderCpk() {
        run("OlderCpk.bin")
    }

    @Test
    fun parseUpgradingCpk() {
        run("UpgradingCpk.bin")
    }

    @Test
    fun parseC5WireTx() {
        run("C5WireTx.bin")
    }

    @Test
    fun parseWithoutOriginalBytes() {
        run("C5WireTx.bin", false)
    }

    private fun run(resourceName: String, includeOriginalBytes: Boolean = true) {
        val bytes = this.javaClass.getResourceAsStream(resourceName)?.readFully()?.sequence()
        requireNotNull(bytes) {
            "Couldn't read resource: $resourceName"
        }
        val decoded = Encoding.decodedBytes(bytes, includeOriginalBytes)
        println(decoded.result.prettyPrint())
    }
}
