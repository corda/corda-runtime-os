package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.Yaml

class EmptyYamlable : Yamlable {
    override fun yamls(namespaceName: String) = emptyList<Yaml>()
}
