package net.corda.cli.plugin.initialRbac.commands

data class PermissionTemplate(val permissionName: String, val permissionString: String, val vnodeShortHash: String?)
