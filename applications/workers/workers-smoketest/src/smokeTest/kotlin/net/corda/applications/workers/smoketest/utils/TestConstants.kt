package net.corda.applications.workers.smoketest.utils

import net.corda.utilities.seconds

val retryTimeout = 120.seconds
val retryInterval = 1.seconds


const val ERROR_CPI_NOT_UPLOADED =
    "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
const val ERROR_IS_CLUSTER_RUNNING =
    "Initial upload failed - is the cluster running?"
const val ERROR_HOLDING_ID =
    "Holding id could not be created - this test needs to be run on a clean cluster."
const val PLATFORM_VERSION =
    "5.2"
