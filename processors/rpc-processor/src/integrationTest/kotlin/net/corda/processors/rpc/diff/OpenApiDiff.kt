package net.corda.processors.rpc.diff

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info

internal fun OpenAPI.diff(baseline: OpenAPI): List<String> {

    val differences = mutableListOf<String>()
    info.diff(baseline.info, differences)
    paths.diff(baseline.paths, differences)
    // todo: tags, components

    return differences
}

private fun Info.diff(baseline: Info, differences: MutableList<String>) {
    if(this != baseline) {
        differences.add("Info is different, baseline: $baseline, current: $this")
    }
}

private fun Paths.diff(baseline: Paths, differences: MutableList<String>) {
    if (size != baseline.size) {
        differences.add("Different number of paths, baseline: ${baseline.size}, current: $size")
    }
    // todo properly compare each path having sorted them
}
