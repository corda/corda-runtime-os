package net.corda.introspiciere.http

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result

class HelloWorldReq(private val endpoint: String) {
    fun greetings(): String {
        val (_, _, result) = "$endpoint/helloworld".httpGet().responseString()
        return when (result) {
            is Result.Failure -> throw result.getException()
            is Result.Success -> result.get()
        }
    }
}

class IdentitiesRequester(private val endpoint: String) {
    fun createKeyAndAddIdentity(alias: String, algorithm: String) {
        val (_, _, result) = "$endpoint/identities".httpPost()
            .jsonBody("""
                {
                    "alias": "$alias",
                    "algorithm": "$algorithm"
                }
            """.trimIndent())
            .responseString()

        when (result) {
            is Result.Failure -> throw result.getException()
            is Result.Success -> result.get()
        }
    }
}