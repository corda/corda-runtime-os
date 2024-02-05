package net.corda.e2etest.utilities.externaldb

data class ConnectionParameters(val jdbc: JDBCString, val user: String, val pass: String)