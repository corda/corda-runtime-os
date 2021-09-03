package net.corda.osgi.api

interface FrameworkService {
    fun getArgs() : Array<String>
    fun setExitCode(exitCode : Int)
}