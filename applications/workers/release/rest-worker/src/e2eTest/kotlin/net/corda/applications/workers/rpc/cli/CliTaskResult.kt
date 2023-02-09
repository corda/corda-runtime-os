package net.corda.applications.workers.rpc.cli

data class CliTaskResult(val exitCode: Int, val stdOut: String, val stdErr: String)
