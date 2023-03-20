package net.corda.applications.workers.rest.cli

data class CliTaskResult(val exitCode: Int, val stdOut: String, val stdErr: String)
