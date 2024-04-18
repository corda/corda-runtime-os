package net.corda.blobinspector

import java.io.ByteArrayOutputStream
import java.io.InputStream

fun InputStream.readFully(): ByteArray {
    val out = ByteArrayOutputStream()
    var next = this.read()
    while (next >= 0) {
        out.write(next)
        next = this.read()
    }
    this.close()
    return out.toByteArray()
}