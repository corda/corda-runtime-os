package net.corda.httprpc.client

data class User(val username: String, val password: String?, val permissions: Set<String>)