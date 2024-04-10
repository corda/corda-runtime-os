package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*

class CordaLifecycleHelper {

    fun startCombinedWorkerWithDockerCompose(
        pidFilePath: String,
        composeFilePath: String,
        dockerProjectName: String,
        cordaRuntimeVersion: String
    ) : Process {
        if (!File(composeFilePath).exists()) {
            throw CordaRuntimeGradlePluginException("Unable to locate compose file: $composeFilePath")
        }

        val pidStore = PrintStream(FileOutputStream(File(pidFilePath)))
        val cordaProcessBuilder = ProcessBuilder(
            "docker",
            "compose",
            "-f",
            composeFilePath,
            "-p",
            dockerProjectName,
            "up",
            "--force-recreate"
        )
        cordaProcessBuilder.environment()["CORDA_RUNTIME_VERSION"] = cordaRuntimeVersion
        cordaProcessBuilder.redirectErrorStream(true)
        val cordaProcess = cordaProcessBuilder.start()
        pidStore.print(cordaProcess.pid())
        cordaProcess.inputStream.transferTo(System.out)
        return cordaProcess
    }

    fun stopCombinedWorkerWithDockerCompose(
        composeFilePath: String,
        dockerProjectName: String
    ) {
        ProcessBuilder(
            "docker",
            "compose",
            "-f",
            composeFilePath,
            "-p",
            dockerProjectName,
            "down"
        )
        .start()
        .waitFor()
    }

    fun stopCombinedWorkerProcess(pidFilePath: String) {
        val cordaPIDFile = File(pidFilePath)
        val sc = Scanner(cordaPIDFile)
        val pid = sc.nextLong()
        sc.close()
        if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("windows")) {
            val parentProcessId: Long? = getAnyParentProcessId(pid)
            killWindowsProcess(pid) // Kill child first
            parentProcessId?.let {
                killWindowsProcess(it)
            }
        } else {
            ProcessBuilder("kill", "-9", "$pid").start()
        }

        val fileDeleted = cordaPIDFile.delete()
        if (!fileDeleted) {
            throw CordaRuntimeGradlePluginException(
                "Failed to delete ${cordaPIDFile.absolutePath}, please remove before starting Corda again."
            )
        }
    }

    private fun getAnyParentProcessId(pid: Long): Long? {
        val findParentProcess = ProcessBuilder(
            "Powershell",
            "-Command",
            "(gwmi win32_process | ? processid -eq  $pid).parentprocessid"
        ).start()
        val parentOutput = findParentProcess.inputStream.bufferedReader().use { it.readText() }.trim()
        return if (parentOutput.isBlank()) {
            null
        } else {
            parentOutput.toLong()
        }
    }

    private fun killWindowsProcess(pid: Long) {
        ProcessBuilder(
            "Powershell",
            "-Command",
            "Stop-Process",
            "-Id",
            "$pid",
            "-PassThru"
        ).start().waitFor()
    }
}