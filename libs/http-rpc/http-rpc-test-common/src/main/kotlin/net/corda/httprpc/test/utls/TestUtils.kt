package net.corda.httprpc.test.utls

import java.net.ServerSocket

fun findFreePort() = ServerSocket(0).use { it.localPort }