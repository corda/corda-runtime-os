package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val X500_BOB = "CN=Bob, OU=Application, O=R3, L=London, C=GB"
const val X500_ALICE = "CN=Alice, OU=Application, O=R3, L=London, C=GB"
//Charlie and David for use in multiple flow status endpoints. Number of flows they start is asserted. Do not start flows using these names
const val X500_CHARLIE = "CN=Charlie, OU=Application, O=R3, L=Dublin, C=IE"
const val X500_DAVID = "CN=David, OU=Application, O=R3, L=Dublin, C=IE"

val TEST_STATIC_MEMBER_LIST: List<String> = listOf(
    X500_ALICE,
    X500_BOB,
    X500_CHARLIE,
    X500_DAVID
)

const val USERNAME = "admin"
const val PASSWORD = "admin"
const val GROUP_ID = "7c5d6948-e17b-44e7-9d1c-fa4a3f667cad"

// The CPB and CPI used in smoke tests
const val TEST_CPI_NAME = "test-cordapp"
const val TEST_CPB_LOCATION = "/META-INF/test-cordapp.cpb"
const val CACHE_INVALIDATION_TEST_CPB = "/META-INF/cache-invalidation-testing/test-cordapp.cpb"
const val VERSION_TESTING_CPB_V1 = "/META-INF/cpi-for-version-upgrade-v1/test-cordapp.cpb"
const val VERSION_TESTING_CPB_V2 = "/META-INF/cpi-for-version-upgrade-v2/test-cordapp.cpb"

val CLUSTER_URI = URI(System.getProperty("rpcHost"))

// BUG:  Not sure if we should be requiring clients to use a method similar to this because we
// return a full hash (64 chars?) but the same API only accepts the first 12 chars.
fun truncateLongHash(shortHash:String):String {
    return shortHash.substring(0,12)
}

fun String.toJson(): JsonNode = ObjectMapper().readTree(this)

fun <K, V> Map<K, V>.toJsonString(): String = ObjectMapper().writeValueAsString(this)

fun Any.contextLogger(): Logger = LoggerFactory.getLogger(javaClass.enclosingClass)

@Throws(InterruptedException::class, TimeoutException::class)
fun <V> Future<V>.getOrThrow(timeout: Duration?): V = try {
    if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)
} catch (e: ExecutionException) {
    throw e.cause!!
}
