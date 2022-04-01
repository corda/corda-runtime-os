package net.corda.httprpc.server.impl.utils

import java.nio.file.Path

fun String.compact() = this.trimMargin().replace("[\n\r]".toRegex(),"")

internal val multipartDir = Path.of(System.getProperty("java.io.tmpdir"), "multipart")
