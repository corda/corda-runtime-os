package net.corda.osgi.framework.api

fun interface ArgsService {
    fun getArgs() : Array<String>
}