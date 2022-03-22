package net.corda.p2p.gateway.messaging.http

import io.netty.util.concurrent.GlobalEventExecutor
import net.corda.test.util.eventually
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.InetSocketAddress

class RoundRobinNameResolverTest {
    @Test
    @Timeout(30)
    fun `round robin resolver return different IPs`() {
        val resolver = RoundRobinNameResolver()
        val executor = GlobalEventExecutor.INSTANCE
        val address = InetSocketAddress.createUnresolved("www.shared_ips.net", 1001)
        val allIps = mutableSetOf<String>()
        eventually(
            duration = 20.seconds,
            waitBetween = 0.seconds
        ) {
            val resolved = resolver.getResolver(executor)
                .resolve(address)
                .get()
            allIps.add(
                resolved.address.address.joinToString(".")
            )

            assertThat(allIps).hasSize(9)
                .containsExactlyInAnyOrder(
                    "1.2.3.1",
                    "1.2.3.2",
                    "1.2.3.3",
                    "1.2.3.4",
                    "1.2.3.5",
                    "1.2.3.6",
                    "1.2.3.7",
                    "1.2.3.8",
                    "1.2.3.9",
                )
        }
    }
}
