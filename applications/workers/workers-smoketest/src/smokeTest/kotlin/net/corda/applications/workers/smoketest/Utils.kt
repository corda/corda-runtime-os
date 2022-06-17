package net.corda.applications.workers.smoketest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.smoketest.virtualnode.helpers.SimpleResponse
import java.net.URI
import java.security.MessageDigest

const val X500_BOB = "CN=Bob, OU=Application, O=R3, L=London, C=GB"
const val X500_ALICE = "CN=Alice, OU=Application, O=R3, L=London, C=GB"
const val CPI_NAME = "flow-worker-dev"

const val USERNAME = "admin"
const val PASSWORD = "admin"
const val GROUP_ID = "placeholder"

val CLUSTER_URI = URI(System.getProperty("rpcHost"))

fun SimpleResponse.toJson(): JsonNode = ObjectMapper().readTree(this.body)!!
fun String.toJson(): JsonNode = ObjectMapper().readTree(this)

fun Any.toJsonString(): String = ObjectMapper().writeValueAsString(this)

// BUG:  Not sure if we should be requiring clients to use a method similar to this because we
// return a full hash (64 chars?) but the same API only accepts the first 12 chars.
fun String.toShortHash(): String = substring(0, 12)




