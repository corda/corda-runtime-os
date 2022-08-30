package net.corda.httprpc.server.impl.context

import io.javalin.http.Context
import net.corda.httprpc.ResponseCode
import net.corda.httprpc.response.ResponseEntity

fun Context.buildJsonResult(result: Any?, returnType: Class<*>) {
    val ctx = this
    when {
        result is ResponseEntity<*> -> {
            // if the responseBody is null, we return null in json
            ctx.json(result.responseBody ?: "null")
                .status(result.responseCode.statusCode)
        }
        (result as? String) != null ->
            ctx.contentType(ContextUtils.contentTypeApplicationJson).result(result).status(ResponseCode.OK.statusCode)
        result != null -> {
            // If the return type does not specify a response code (is not a HttpResponse) we default the status to 200 - OK.
            ctx.json(result).status(ResponseCode.OK.statusCode)
        }
        else -> {
            val methodHasReturnType = returnType != Void.TYPE
            if (methodHasReturnType) {
                // if the method has a return type and returned null we return a status code 200 - OK with null payload
                ctx.result("null").status(ResponseCode.OK.statusCode)
            } else {
                // if the method has no return type we return a status code 204 - No Content.
                ctx.status(ResponseCode.NO_CONTENT.statusCode)
            }
        }
    }
}