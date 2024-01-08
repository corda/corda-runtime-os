package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "dynamic",
    subcommands = [
        OnboardMgm::class,
        OnboardMember::class,
    ],
    mixinStandardHelpOptions = true,
    description = ["For onboarding MGM and members to a dynamic application network"],
)
class Dynamic
