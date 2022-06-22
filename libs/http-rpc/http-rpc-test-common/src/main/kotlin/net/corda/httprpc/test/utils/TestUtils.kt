package net.corda.httprpc.test.utils

import java.net.ServerSocket

fun findFreePort() = ServerSocket(0).use { it.localPort }