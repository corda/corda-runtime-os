package net.corda.e2etest.utilities

import java.io.InputStream

interface HttpsClient {
    fun postMultiPart(cmd: String, fields: Map<String, String>, files: Map<String, HttpsClientFileUpload>): SimpleResponse
    fun post(cmd: String, body: String): SimpleResponse
    fun put(cmd: String, body: String): SimpleResponse
    fun putMultiPart(cmd: String, fields: Map<String, String>, files: Map<String, HttpsClientFileUpload>): SimpleResponse
    fun get(cmd: String): SimpleResponse
    fun delete(cmd: String): SimpleResponse
}

data class HttpsClientFileUpload(val content: InputStream, val filename: String)