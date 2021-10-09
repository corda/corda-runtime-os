package net.corda.httprpc

import io.swagger.v3.oas.models.tags.Tag

interface Controller {

    val tag: Tag? get() = null

    fun register()
}