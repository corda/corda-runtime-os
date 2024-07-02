package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.CombinedWorkerHelper.startCompose
import net.corda.gradle.plugin.CombinedWorkerHelper.stopCompose
import net.corda.gradle.plugin.SmokeTestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test

class VNodeSetupTaskTest : SmokeTestBase() {
    @BeforeEach
    fun startCombinedWorker() {
        startCompose()
    }

    @AfterEach
    fun stopCombinedWorker() {
        stopCompose()
    }

    @Test
    fun `create static network succeeds`() {
        executeWithRunner(
            VNODE_SETUP_TASK_NAME,
            "--info", "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = true
        ).task(":$VNODE_SETUP_TASK_NAME")!!.assertTaskSucceeded()
    }

    @Test
    fun `create dynamic network succeeds`() {
        executeWithRunner(
            VNODE_SETUP_TASK_NAME,
            "--info", "--stacktrace",
            forwardOutput = true,
            isStaticNetwork = false
        ).task(":$VNODE_SETUP_TASK_NAME")!!.assertTaskSucceeded()
    }
}
