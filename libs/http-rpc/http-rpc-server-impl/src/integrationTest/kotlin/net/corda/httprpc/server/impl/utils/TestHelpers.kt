package net.corda.httprpc.server.impl.utils

fun String.compact() = this.trimMargin().replace("[\n\r]".toRegex(),"")