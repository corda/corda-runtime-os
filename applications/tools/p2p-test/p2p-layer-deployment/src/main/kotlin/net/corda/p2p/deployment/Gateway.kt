package net.corda.p2p.deployment

class Gateway(
    index: Int,
    kafkaServers: String,
    override val hosts: Collection<String>,
    tag: String,
) : P2pPod(kafkaServers, tag) {
    companion object {
        fun gateways(
            count: Int,
            hostNames: Collection<String>,
            kafkaServers: String,
            tag: String,
        ): Collection<Pod> {
            val gateways = (1..count).map {
                Gateway(it, kafkaServers, hostNames, tag)
            }
            val balancer = LoadBalancer(
                hostNames,
                gateways.map { it.app }
            )
            return gateways + balancer
        }
    }
    override val app = "gateway-$index"
    override val imageName = "p2p-gateway"
    override val environmentVariables = mapOf(
        "KAFKA_SERVERS" to kafkaServers,
        "INSTANCE_ID" to index.toString(),
    )
    override val ports = listOf(
        Port("p2p-gateway", 80)
    )
    /*

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
        var jarFile = new File("/opt/override/jars/bin/corda-p2p-gateway-5.0.0.0-SNAPSHOT.jar");
        while (true) {
            System.out.println("Waiting for jar....");
            Thread.sleep(10000);
            if(jarFile.canRead()) {
                System.out.println("Waiting a little longer....");
                Thread.sleep(10000);
                System.out.println("Running...");
                var gateway = new ProcessBuilder("java", "-jar", jarFile.getAbsolutePath())
                        .inheritIO()
                        .start();
                gateway.waitFor();
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
*/
    // override val image = "corda-os-docker-dev.software.r3.com/corda-os-p2p-gateway"

    // YIFT: Remove this when can access images
    /*
    override val image = "node"
    override val command = listOf("node", "/src/go.js")
    override val rawData = listOf(
        TextRawData(
            "go", "/src",
            listOf(
                TextFile(
                    "go.js",
                    """
    const http = require('http')
    const port = 80

    const requestHandler = (request, response) => {
      console.log(request.url)
      response.end('Hello Node.js Server - ' + $index)
    }

    const server = http.createServer(requestHandler)

    server.listen(port, '${hosts?.firstOrNull() ?: "0.0.0.0"}', (err) => {
      if (err) {
        return console.log('something bad happened', err)
      }

      console.log(`server is listening on ${80}`)
    })
            """
                )
            )
        )
    )

    override val ports = listOf(
        Port("http", 80)
    )*/
}
