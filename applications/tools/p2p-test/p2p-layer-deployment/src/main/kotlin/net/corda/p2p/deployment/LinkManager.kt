package net.corda.p2p.deployment

class LinkManager(
    index: Int,
    kafkaServers: String,
) : Pod() {
    companion object {
        fun linkManagers(count: Int, kafkaServers: String) = (1..count).map {
            LinkManager(it, kafkaServers)
        }
    }
    override val app = "link-manager-$index"
    override val image = "azul/zulu-openjdk-alpine:11"
    override val command = listOf("java", "/src/RunMe.java")
    override val environmentVariables = mapOf("KAFKA_SERVERS" to kafkaServers)

    override val rawData = listOf(
        TextRawData(
            "start", "/src",
            listOf(
                TextFile(
                    "RunMe.java",
                    """
import java.io.File;

public class RunMe {
    public static void main(String... args) throws Exception {
        var jarFile = new File("/opt/override/jars/bin/corda-p2p-link-manager-5.0.0.0-SNAPSHOT.jar");
        while (true) {
            System.out.println("Waiting for jar....");
            Thread.sleep(10000);
            if(jarFile.canRead()) {
                System.out.println("Waiting a little longer....");
                Thread.sleep(10000);
                System.out.println("Running...");
                var manager = new ProcessBuilder("java", "-jar", jarFile.getAbsolutePath())
                        .inheritIO()
                        .start();
                manager.waitFor();
                return;
            }
        }
    }
}
                    """.trimIndent()
                )
            )
        )
    )
    // override val image = "engineering-docker-dev.software.r3.com/corda-os-p2p-link-manager"
}
