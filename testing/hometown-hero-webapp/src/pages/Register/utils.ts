import { FULL_NAME_SPLITTER } from '@/constants/fullNameSplit';
import { NotificationService } from '@r3/r3-tooling-design-system/exports';
import adminAxiosInstance from '@/api/adminAxios';
import apiCall from '@/api/apiCall';

export const createVNode = async (x500Name: string, cpiFileChecksum: string): Promise<boolean> => {
    const response = await apiCall({
        method: 'post',
        path: '/api/v1/virtualnode',
        params: {
            request: {
                cpiFileChecksum: cpiFileChecksum,
                x500Name: x500Name,
            },
        },
        axiosInstance: adminAxiosInstance,
    });
    if (response.error) {
        NotificationService.notify(`Failed to create VNode: Error: ${response.error}`, 'Error', 'danger');
        return false;
    } else {
        NotificationService.notify(`Successfully created VNode!`, 'Success!', 'success');
    }
    return true;
};

export const createUser = async (username: string, password: string, holdingShortId: string): Promise<boolean> => {
    const response = await apiCall({
        method: 'post',
        path: '/api/v1/user',
        params: {
            createUserType: {
                enabled: true,
                fullName: `${username}${FULL_NAME_SPLITTER}${holdingShortId}`,
                initialPassword: password,
                loginName: username,
            },
        },
        axiosInstance: adminAxiosInstance,
    });
    if (response.error) {
        NotificationService.notify(
            `Failed to create user with username: ${username}: Error: ${response.error}`,
            'Error',
            'danger'
        );
        return false;
    } else {
        NotificationService.notify(`Successfully created user!`, 'Success!', 'success');
    }

    return true;
};

export const createPermission = async (
    permissionString: string,
    permissionType: 'DENY' | 'ALLOW'
): Promise<string | undefined> => {
    const response = await apiCall({
        method: 'post',
        path: '/api/v1/permission',
        params: {
            createPermissionType: {
                permissionString: permissionString,
                permissionType: permissionType,
            },
        },
        axiosInstance: adminAxiosInstance,
    });
    if (response.error) {
        NotificationService.notify(
            `Failed to create permission ${permissionString} : Error: ${response.error}`,
            'Error',
            'danger'
        );
        return undefined;
    }

    return response.data.id;
};

export const createRole = async (): Promise<string | undefined> => {
    const response = await apiCall({
        method: 'post',
        path: '/api/v1/role',
        params: {
            createRoleType: {
                roleName: 'user_role',
            },
        },
        axiosInstance: adminAxiosInstance,
    });
    if (response.error) {
        NotificationService.notify(`Failed to create new role: Error: ${response.error}`, 'Error', 'danger');
        return undefined;
    } else {
        NotificationService.notify(`Successfully created new role for user!`, 'Success!', 'success');
    }

    return response.data.id;
};

export const addPermissionToRole = async (permissionId: string, roleId: string) => {
    const response = await apiCall({
        method: 'put',
        path: '/api/v1/role/addpermission',
        params: {
            permissionId: permissionId,
            roleId: roleId,
        },
        axiosInstance: adminAxiosInstance,
    });
    if (response.error) {
        NotificationService.notify(
            `Failed to add permission ${permissionId} to role: ${roleId} : Error: ${response.error}`,
            'Error',
            'danger'
        );
        return false;
    }

    return true;
};

export const addRoleToUser = async (loginName: string, roleId: string): Promise<boolean> => {
    const response = await apiCall({
        method: 'put',
        path: '/api/v1/user/addrole',
        params: {
            loginName: loginName,
            roleId: roleId,
        },
        axiosInstance: adminAxiosInstance,
    });
    if (response.error) {
        NotificationService.notify(
            `Failed to add role ${roleId} to user: ${loginName} : Error: ${response.error}`,
            'Error',
            'danger'
        );
        return false;
    } else {
        NotificationService.notify(`Successfully added role to new user!`, 'Success!', 'success');
    }

    return true;
};
