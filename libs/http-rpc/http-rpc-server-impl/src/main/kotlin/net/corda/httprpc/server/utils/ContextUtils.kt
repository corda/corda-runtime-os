package net.corda.httprpc.server.utils

import io.javalin.http.Context

internal fun Context.addHeaderValues(name: String, values: Iterable<String>) {
    values.forEach {
        this.res.addHeader(name, it)
    }
}
