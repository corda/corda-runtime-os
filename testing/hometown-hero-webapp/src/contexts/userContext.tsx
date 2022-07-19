import { FULL_NAME_SPLITTER } from '@/constants/fullNameSplit';
import { NotificationService } from '@r3/r3-tooling-design-system/exports';
import { VirtualNode } from '@/models/virtualnode';
import apiCall from '@/api/apiCall';
import { axiosInstance } from '@/api/axiosConfig';
import createCtx from './createCtx';
import { useSessionStorage } from '@/hooks/useSessionStorage';

type UserContextProps = {
    holderShortId: string;
    username: string;
    password: string;
    vNode: VirtualNode | undefined;
    setVNode: (vNode: VirtualNode | undefined) => void;
    login: (username: string, password: string) => Promise<boolean>;
    clearData: () => void;
    saveLoginDetails: (username: string, password: string, vNode?: VirtualNode) => void;
};

const [useUserContext, Provider] = createCtx<UserContextProps>();

export default useUserContext;

type ProviderProps = {
    children?: React.ReactNode;
};

export const UserContextProvider: React.FC<ProviderProps> = ({ children }) => {
    const [vNode, setVNode] = useSessionStorage<VirtualNode | undefined>('virtualNode', undefined);
    const [holderShortId, setHolderShortId] = useSessionStorage<string>('holdershortid', '');
    const [username, setUsername] = useSessionStorage<string>('username', '');
    const [password, setPassword] = useSessionStorage<string>('password', '');

    const login = async (username: string, password: string) => {
        const response = await apiCall({
            method: 'get',
            path: `/api/v1/user?loginname=${username}`,
            auth: {
                username: username,
                password: password,
            },
        });

        if (response.error) {
            axiosInstance.defaults.headers.common = { Authorization: '' };
            NotificationService.notify(
                `Failed to Sign in with the provided username and password: Error: ${response.error}`,
                'Error',
                'danger'
            );
            return false;
        } else {
            NotificationService.notify(`Successfully Signed in!`, 'Success!', 'success');
            setHolderShortId(response.data.fullName.split(FULL_NAME_SPLITTER)[1]);
            return true;
        }
    };

    const saveLoginDetails = (username: string, password: string, vNode?: VirtualNode) => {
        setUsername(username);
        setPassword(password);
        setVNode(vNode);
    };

    const clearData = () => {
        setUsername('');
        setPassword('');
        setVNode(undefined);
        setHolderShortId('');
    };

    return (
        <Provider value={{ holderShortId, username, password, vNode, login, clearData, saveLoginDetails, setVNode }}>
            {children}
        </Provider>
    );
};
