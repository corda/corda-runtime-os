package net.corda.applications.workers.smoketest

import java.net.URI

const val X500_BOB = "CN=Bob, OU=Application, O=R3, L=London, C=GB"
const val X500_ALICE = "CN=Alice, OU=Application, O=R3, L=London, C=GB"
const val X500_CHARLIE = "CN=Charlie, OU=Application, O=R3, L=Dublin, C=IE"
const val CPI_NAME = "flow-worker-dev"

const val USERNAME = "admin"
const val PASSWORD = "admin"
const val GROUP_ID = "placeholder"

val CLUSTER_URI = URI(System.getProperty("rpcHost"))


// BUG:  Not sure if we should be requiring clients to use a method similar to this because we
// return a full hash (64 chars?) but the same API only accepts the first 12 chars.
fun truncateLongHash(shortHash:String):String {
    return shortHash.substring(0,12)
}




