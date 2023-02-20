package net.corda.e2etest.utilities

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

const val USERNAME = "admin"
val PASSWORD = System.getenv("INITIAL_ADMIN_USER_PASSWORD") ?: "admin"
const val GROUP_ID = "7c5d6948-e17b-44e7-9d1c-fa4a3f667cad"
val testRunUniqueId: UUID = UUID.randomUUID()

// Code signer certificate
const val CODE_SIGNER_CERT = "/cordadevcodesign.pem"

// The CPB and CPI used in smoke tests
const val TEST_NOTARY_CPI_NAME = "test-notary-server-cordapp"
const val TEST_NOTARY_CPB_LOCATION = "/META-INF/notary-plugin-non-validating-server.cpb"

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

/**
 *This is a crude method for getting the holdingID short hash.
 */
fun getHoldingIdShortHash(x500Name: String, groupId: String): String {
    val s = MemberX500Name.parse(x500Name).toString() + groupId
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    return digest.digest(s.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte).uppercase() }
        .substring(0, 12)
}
