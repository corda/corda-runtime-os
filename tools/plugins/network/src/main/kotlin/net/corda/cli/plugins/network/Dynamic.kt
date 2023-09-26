package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "dynamic",
    subcommands = [
        OnboardMgm::class,
        OnBoardMember::class,
    ],
    mixinStandardHelpOptions = true,
    description = ["For Onboarding Member and MGM"]
)
class Dynamic