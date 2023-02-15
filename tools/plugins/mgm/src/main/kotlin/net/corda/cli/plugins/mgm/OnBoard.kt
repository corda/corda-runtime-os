package net.corda.cli.plugins.mgm

import picocli.CommandLine.Command

@Command(
    name = "onboard",
    subcommands = [
        OnboardMgm::class,
        OnBoardMember::class,
        AllowClientCertificate::class,
    ],
    description = ["On board a member."]
)
class OnBoard