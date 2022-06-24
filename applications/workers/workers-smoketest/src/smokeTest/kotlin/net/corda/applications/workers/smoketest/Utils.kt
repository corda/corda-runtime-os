package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Instant
import net.corda.applications.workers.smoketest.virtualnode.helpers.ClusterBuilder
import net.corda.applications.workers.smoketest.virtualnode.helpers.SimpleResponse

const val ERROR_HOLDING_ID =
    "Holding id could not be created - this test needs to be run on a clean cluster."

const val RPC_FLOW_STATUS_SUCCESS = "COMPLETED"
const val RPC_FLOW_STATUS_FAILED = "FAILED"

const val X500_BOB = "CN=Bob, OU=Application, O=R3, L=London, C=GB"
const val X500_ALICE = "CN=Alice, OU=Application, O=R3, L=London, C=GB"
const val X500_CAROL = "CN=Carol, OU=Application, O=R3, L=London, C=GB"
const val FLOW_WORKER_DEV_CPI_NAME = "flow-worker-dev"
const val CALCULATOR_CPI_NAME = "calculator"

const val USERNAME = "admin"
const val PASSWORD = "admin"
const val GROUP_ID = "placeholder"

val CLUSTER_URI = URI(System.getProperty("rpcHost"))


// BUG:  Not sure if we should be requiring clients to use a method similar to this because we
// return a full hash (64 chars?) but the same API only accepts the first 12 chars.
fun truncateLongHash(shortHash:String):String {
    return shortHash.substring(0,12)
}

fun SimpleResponse.toJson(): JsonNode = ObjectMapper().readTree(this.body)!!
fun String.toJson(): JsonNode = ObjectMapper().readTree(this)

