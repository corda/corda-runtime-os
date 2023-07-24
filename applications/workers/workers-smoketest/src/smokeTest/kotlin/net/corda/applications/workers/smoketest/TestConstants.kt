package net.corda.applications.workers.smoketest

import java.util.UUID
import net.corda.utilities.seconds

val retryTimeout = 120.seconds
val retryInterval = 1.seconds

val defaultTestRunId: UUID by lazy { UUID.randomUUID() }
val defaultGroupId: UUID by lazy { UUID.randomUUID() }

val defaultStaticMemberList = listOf(
    "CN=Alice-${defaultTestRunId}, OU=Application, O=R3, L=London, C=GB",
    "CN=Bob-${defaultTestRunId}, OU=Application, O=R3, L=London, C=GB"
)

const val ERROR_CPI_NOT_UPLOADED =
    "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
const val ERROR_IS_CLUSTER_RUNNING =
    "Initial upload failed - is the cluster running?"
