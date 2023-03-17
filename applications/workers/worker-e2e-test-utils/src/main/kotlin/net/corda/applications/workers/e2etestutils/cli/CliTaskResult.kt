package net.corda.applications.workers.e2etestutils.cli

data class CliTaskResult(val exitCode: Int, val stdOut: String, val stdErr: String)
