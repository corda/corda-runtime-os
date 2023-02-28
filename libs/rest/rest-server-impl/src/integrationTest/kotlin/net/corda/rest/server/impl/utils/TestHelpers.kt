package net.corda.rest.server.impl.utils

fun String.compact() = this.trimMargin().replace("[\n\r]".toRegex(),"")