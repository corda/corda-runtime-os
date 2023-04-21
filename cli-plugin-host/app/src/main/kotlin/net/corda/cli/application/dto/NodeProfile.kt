package net.corda.cli.application.dto

data class NodeProfile(
    var urlRoot: String
)
{
    override fun toString(): String {
        return "Node Profile Url Root: $urlRoot"
    }
}