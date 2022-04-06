package net.corda.applications.workers.smoketest.virtualnode.helpers

import java.io.InputStream

interface HttpsClient {
    fun postMultiPart(cmd: String, fileName: String, inputStream: InputStream): SimpleResponse
    fun post(cmd: String, body: String): SimpleResponse
    fun put(cmd: String, body: String): SimpleResponse
    fun get(cmd: String): SimpleResponse
}
