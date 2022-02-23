package net.corda.introspiciere.http

import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response

internal fun Response.buildException(): HttpException? {
    if (body().isEmpty()) return null
    val message = StringBuilder()
    message.appendLine(responseMessage)
    message.appendLine("Url : $url")
    val appendHeaderWithValue = { key: String, value: String -> message.appendLine("$key : $value") }
    headers.transformIterate(appendHeaderWithValue)
    message.appendLine("Body : ${body().asString(headers[Headers.CONTENT_TYPE].lastOrNull())}")
    return HttpException(statusCode, message.toString())
}