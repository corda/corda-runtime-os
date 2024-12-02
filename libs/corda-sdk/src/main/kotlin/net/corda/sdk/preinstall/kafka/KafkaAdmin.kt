package net.corda.sdk.preinstall.kafka

import net.corda.sdk.preinstall.report.Report
import net.corda.sdk.preinstall.report.ReportEntry
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.common.Node
import java.util.Properties

class KafkaAdmin(props: Properties, report: Report) {
    private val admin: AdminClient?

    init {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = this::class.java.classLoader

            admin = AdminClient.create(props)
            report.addEntry(ReportEntry("Created admin client successfully", true))
        } finally {
            Thread.currentThread().contextClassLoader = contextCL
        }
    }

    fun getNodes(): Collection<Node> {
        return admin?.describeCluster()
            ?.nodes()
            ?.get()
            ?: listOf()
    }

    fun getDescriptionID(): String? {
        return admin?.describeCluster()?.clusterId()?.get()
    }
}
