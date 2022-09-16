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
const val CPI_NAME = "flow-worker-dev"

const val USERNAME = "admin"
const val PASSWORD = "admin"
const val GROUP_ID = "7c5d6948-e17b-44e7-9d1c-fa4a3f667cad"

val CLUSTER_URI = URI(System.getProperty("rpcHost"))


// BUG:  Not sure if we should be requiring clients to use a method similar to this because we
// return a full hash (64 chars?) but the same API only accepts the first 12 chars.
fun truncateLongHash(shortHash:String):String {
    return shortHash.substring(0,12)
}

fun String.toJson(): JsonNode = ObjectMapper().readTree(this)

fun Any.contextLogger(): Logger = LoggerFactory.getLogger(javaClass.enclosingClass)

@Throws(InterruptedException::class, TimeoutException::class)
fun <V> Future<V>.getOrThrow(timeout: Duration?): V = try {
    if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)
} catch (e: ExecutionException) {
    throw e.cause!!
}