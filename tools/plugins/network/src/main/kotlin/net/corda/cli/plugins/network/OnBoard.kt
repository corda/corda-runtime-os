package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "onboard",
    subcommands = [
        OnboardMgm::class,
        OnBoardMember::class,
        AllowClientCertificate::class,
    ],
    description = ["On board a member."],
    hidden = true
)
class OnBoard