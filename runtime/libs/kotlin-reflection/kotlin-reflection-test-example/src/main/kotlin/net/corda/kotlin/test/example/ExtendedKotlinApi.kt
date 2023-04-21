package net.corda.kotlin.test.example

import net.corda.kotlin.test.api.KotlinApi

interface ExtendedKotlinApi : KotlinApi {
    val neverNull: String

    val listOfItems: List<String>

    override fun anything(): List<Any?>
}
