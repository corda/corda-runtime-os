package net.corda.processors.rpc.diff

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Info

internal fun OpenAPI.diff(baseline: OpenAPI): List<String> {
    return info.diff(baseline.info) +
            paths.diff(baseline.paths)
    // todo: tags, components
}

private fun Info.diff(baseline: Info): List<String> {
    return if (this != baseline) {
        listOf("Info is different, baseline: $baseline, current: $this")
    } else emptyList()
}

private fun Paths.diff(baseline: Paths): List<String> {
    val differences = mutableListOf<String>()
    if (size != baseline.size) {
        differences.add("Different number of paths, baseline: ${baseline.size}, current: $size")
    }
    // todo: properly compare each path having sorted them first
    return differences
}
