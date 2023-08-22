package net.corda.web.client

import java.net.URL

interface CordaHttpClient {

    suspend fun post(url: URL, payload: ByteArray) : ByteArray

}