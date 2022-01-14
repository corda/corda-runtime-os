package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.Yaml

interface Yamlable {
    fun yamls(namespaceName: String): Collection<Yaml>
}
