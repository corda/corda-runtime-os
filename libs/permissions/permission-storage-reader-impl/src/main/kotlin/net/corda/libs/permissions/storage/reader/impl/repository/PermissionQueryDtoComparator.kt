package net.corda.libs.permissions.storage.reader.impl.repository

import net.corda.permissions.model.PermissionType
import net.corda.permissions.query.dto.InternalPermissionQueryDto

class PermissionQueryDtoComparator : Comparator<InternalPermissionQueryDto> {
    override fun compare(o1: InternalPermissionQueryDto, o2: InternalPermissionQueryDto): Int {
        return when {
            o1.permissionType != o2.permissionType -> {
                // DENY before ALLOW
                if (o1.permissionType == PermissionType.DENY) {
                    -1
                } else {
                    1
                }
            }
            o1.permissionString != o2.permissionString -> {
                // then permission string alphabetical order
                o1.permissionString.compareTo(o2.permissionString)
            }
            o1.virtualNode != o2.virtualNode -> {
                // then virtual node null first
                if (o1.virtualNode == null) {
                    -1
                } else if (o2.virtualNode == null) {
                    1
                } else {
                    // then virtualNode alphabetical order
                    o1.virtualNode!!.compareTo(o2.virtualNode!!)
                }
            }
            o1.groupVisibility != o2.groupVisibility -> {
                // then groupVisibility null first
                if (o1.groupVisibility == null) {
                    -1
                } else if (o2.groupVisibility == null) {
                    1
                } else {
                    // then groupVisibility alphabetical order
                    o1.groupVisibility!!.compareTo(o2.groupVisibility!!)
                }
            }
            o1.id != o2.id -> {
                // then ID will be sorted alphabetically
                o1.id.compareTo(o2.id)
            }
            else -> 0
        }
    }
}