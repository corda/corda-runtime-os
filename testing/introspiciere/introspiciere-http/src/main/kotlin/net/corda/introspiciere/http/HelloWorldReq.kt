package net.corda.introspiciere.http

import com.github.kittinunf.fuel.httpGet
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