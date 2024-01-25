package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.retry
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*

class CordaLifecycleHelper {

    fun startPostgresContainer(containerName: String) : Process {
        val dockerCmdList = listOf(
            "docker",
            "run",
            "-d",
            "--rm",
            "-p",
            "5432:5432",
            "--name",
            containerName,
            "-e",
            "POSTGRES_DB=cordacluster",
            "-e",
            "POSTGRES_USER=postgres",
            "-e",
            "POSTGRES_PASSWORD=password",
            "postgres:latest"
        )

        val dockerProcessBuilder = ProcessBuilder(dockerCmdList)
        val dockerProcess = dockerProcessBuilder.start()
        dockerProcess.waitFor()
        return dockerProcess
    }

    fun waitForContainerStatus(containerName: String) {
        val dockerStatusCmd = listOf(
            "docker",
            "ps",
            "-f",
            "name=$containerName",
            "--format",
            "{{.State}}"
        )
        val dockerProcess = ProcessBuilder(dockerStatusCmd).start()
        dockerProcess.waitFor()

        var containerStatus: String
        retry {
            containerStatus = dockerProcess.inputStream.bufferedReader().use { it.readText() }
            isContainerRunning(containerName, containerStatus)
        }
    }

    private fun isContainerRunning(containerName: String, containerStatus: String) {
        if (!containerStatus.contains("running")) {
            throw CordaRuntimeGradlePluginException("Expected $containerName to be `running` but was `$containerStatus`")
        }
    }

    fun stopDockerContainer(containerName: String) {
        ProcessBuilder("docker", "stop", containerName).start()
    }

    fun startCombinedWorkerProcess(
        pidFilePath: String,
        combinedWorkerJarFilePath: String,
        javaBinDir: String,
        jdbcDir: String,
        projectRootDir: String
    ) : Process {
        val pidStore = PrintStream(FileOutputStream(File(pidFilePath)))
        val cordaCmdList = listOf(
            "$javaBinDir/java",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
            "-Dlog4j.configurationFile=$projectRootDir/config/log4j2.xml",
            "-Dco.paralleluniverse.fibers.verifyInstrumentation=true",
            "-jar",
            combinedWorkerJarFilePath,
            "--instance-id=0",
            "-mbus.busType=DATABASE",
            "-spassphrase=password",
            "-ssalt=salt",
            "-ddatabase.user=user",
            "-ddatabase.pass=password",
            "-ddatabase.jdbc.url=jdbc:postgresql://localhost:5432/cordacluster",
            "-ddatabase.jdbc.directory=$jdbcDir"
        )

        val cordaProcessBuilder = ProcessBuilder(cordaCmdList)
        cordaProcessBuilder.redirectErrorStream(true)
        val cordaProcess = cordaProcessBuilder.start()
        pidStore.print(cordaProcess.pid())
        cordaProcess.inputStream.transferTo(System.out)
        return cordaProcess
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