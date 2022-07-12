import { Button, Checkbox, NotificationService, PasswordInput, TextInput } from '@r3/r3-tooling-design-system/exports';
import {
    addPermissionToRole,
    addRoleToUser,
    createAllowPermission,
    createRole,
    createUser,
    createVNode,
} from './utils';
import { useEffect, useState } from 'react';

import FormContentWrapper from '@/components/FormContentWrapper/FormContentWrapper';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import RegisterViz from '@/components/Visualizations/RegisterViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import { trackPromise } from 'react-promise-tracker';
import useAppDataContext from '@/contexts/appDataContext';
import { useNavigate } from 'react-router-dom';
import useUserContext from '@/contexts/userContext';

const Register = () => {
    const { saveLoginDetails } = useUserContext();

    const navigate = useNavigate();

    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [confirmPassword, setConfirmPassword] = useState<string>('');
    const [isUserAndPasswordSaved, setIsUserAndPasswordSaved] = useState<boolean>(true);

    const { refreshVNodes, cpiList, refreshCpiList } = useAppDataContext();

    useEffect(() => {
        refreshCpiList();
    }, []);

    const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = event.target;
        if (name === 'username') {
            setUsername(value);
        }
        if (name === 'password') {
            setPassword(value);
        }
        if (name === 'confirm_password') {
            setConfirmPassword(value);
        }
    };

    const handleCheckboxClick = () => {
        setIsUserAndPasswordSaved((prev) => !prev);
    };

    const handleSubmit = async () => {
        //If theres no cpis prevent user from registering
        if (cpiList.length === 0) {
            NotificationService.notify(
                `No CPIs are uploaded to the cluster. Cannot register a new VNode and User.`,
                'Error',
                'danger'
            );
            return;
        }

        const cpiFileChecksum = cpiList[0].fileChecksum;

        const x500Name = `CN=${username}, O=${username} node, L=LDN, C=GB`;

        const vNodeCreated = await createVNode(x500Name, cpiFileChecksum);
        if (!vNodeCreated) return;

        const userCreated = await createUser(username, password);
        if (!userCreated) return;

        //give some time for vNodes list to update
        await new Promise((r) => setTimeout(r, 2000));

        const updatedVNodes = await refreshVNodes();

        const holdershortid = updatedVNodes.find((vNode) => vNode.holdingIdentity.x500Name === x500Name);

        if (!holdershortid) {
            NotificationService.notify(
                `Could not find newly created VNode with x500 name: ${x500Name}.`,
                'Error',
                'danger'
            );
            return;
        }

        const postPermissionId = await createAllowPermission(`POST:/api/v1/flow/${holdershortid}`);
        if (!postPermissionId) return;

        const getPermissionId = await createAllowPermission(`GET:/api/v1/flow/${holdershortid}/*`);
        if (!getPermissionId) return;

        const roleId = await createRole();

        if (!roleId) return;

        const addedPostPermission = await addPermissionToRole(postPermissionId, roleId);

        if (!addedPostPermission) return;

        const addedGetPermission = await addPermissionToRole(getPermissionId, roleId);

        if (!addedGetPermission) return;

        const addedRoleToUser = await addRoleToUser(username, roleId);

        if (!addedRoleToUser) return;

        NotificationService.notify(`Registration complete!`, 'Success!', 'success');
    };

    const canSubmit = username.length > 0 && password.length > 0 && confirmPassword === password;

    return (
        <PageContentWrapper>
            <PageHeader withBackButton>Register V-Node</PageHeader>
            <FormContentWrapper>
                {/* Maybe by fetching all of the node names we can check if the "username" is available to make things smoother */}
                <TextInput
                    required
                    name="username"
                    label={'Username'}
                    value={username}
                    onChange={handleInputChange}
                    invalid={username.length === 0}
                />
                <PasswordInput
                    required
                    name="password"
                    label={'Password*'}
                    value={password}
                    onChange={handleInputChange}
                    invalid={password.length === 0}
                />
                <PasswordInput
                    required
                    name="confirm_password"
                    label={'Confirm Password*'}
                    value={confirmPassword}
                    onChange={handleInputChange}
                    invalid={confirmPassword !== password || confirmPassword.length === 0}
                />
                <Checkbox
                    disabled={!canSubmit}
                    value={''}
                    checked={isUserAndPasswordSaved}
                    onChange={handleCheckboxClick}
                >
                    Save username and password
                </Checkbox>
                <Button
                    style={{ width: 142 }}
                    className="h-12"
                    size={'large'}
                    variant={'primary'}
                    disabled={!canSubmit}
                    onClick={() => {
                        trackPromise(handleSubmit());
                    }}
                >
                    Register
                </Button>
            </FormContentWrapper>
            <VisualizationWrapper width={700}>
                <RegisterViz />
            </VisualizationWrapper>
        </PageContentWrapper>
    );
};

export default Register;
