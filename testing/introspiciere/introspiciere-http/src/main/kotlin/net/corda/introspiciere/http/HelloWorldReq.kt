package net.corda.introspiciere.http

import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import java.time.LocalDateTime

class HelloWorldReq(private val endpoint: String) {
    fun greetings(): String {
        println("${LocalDateTime.now()} Greetings")
        val (_, response, result) = "$endpoint/helloworld".httpGet().timeoutRead(180000).responseString()
        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

class Topics(private val endpoint: String) {
    fun fetch(): String {
        println("${LocalDateTime.now()} Fetching kafka topics")
        val (_, response, result) = "$endpoint/topics".httpGet().timeoutRead(180000).responseString()
        return when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

class IdentitiesRequester(private val endpoint: String) {
    fun createKeyAndAddIdentity(alias: String, algorithm: String) {
        println("${LocalDateTime.now()} Creating key and identity for $alias using $algorithm")
        val (_, response, result) = "$endpoint/identities".httpPost()
            .jsonBody("""
                {
                    "alias": "$alias",
                    "algorithm": "$algorithm"
                }
            """.trimIndent())
            .timeoutRead(180000)
            .responseString()
        when (result) {
            is Result.Success -> result.get()
            is Result.Failure -> throw IntrospiciereException(result.getException().message, response.buildException())
        }
    }
}

private fun Response.buildException(): HttpException? {
    if (body().isEmpty()) return null
    val message = StringBuilder()
    message.appendLine(responseMessage)
    message.appendLine("Url : $url")
    val appendHeaderWithValue = { key: String, value: String -> message.appendLine("$key : $value") }
    headers.transformIterate(appendHeaderWithValue)
    message.appendLine("Body : ${body().asString(headers[Headers.CONTENT_TYPE].lastOrNull())}")
    return HttpException(statusCode, message.toString())
}

class IntrospiciereException(message: String?, cause: Throwable?) : Exception(message, cause)