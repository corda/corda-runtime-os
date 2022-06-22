import { NotificationService } from '@r3/r3-tooling-design-system/exports';
import apiCall from '@/api/apiCall';
import { axiosInstance } from '@/api/axiosConfig';
import createCtx from './createCtx';
import { useEffect } from 'react';
import { useSessionStorage } from '@/hooks/useSessionStorage';

type UserContextProps = {
    username: string;
    password: string;
    login: (username: string, password: string) => Promise<boolean>;
    saveLoginDetails: (username: string, password: string) => void;
};

const [useUserContext, Provider] = createCtx<UserContextProps>();

export default useUserContext;

type ProviderProps = {
    children?: React.ReactNode;
};

export const UserContextProvider: React.FC<ProviderProps> = ({ children }) => {
    const [username, setUsername] = useSessionStorage<string>('username', '');
    const [password, setPassword] = useSessionStorage<string>('password', '');

    useEffect(() => {
        const encodedHeader = btoa(`${username}:${password}`);
        axiosInstance.defaults.headers.common = { Authorization: `Basic ${encodedHeader}` };
    }, []);

    const login = async (username: string, password: string) => {
        const encodedHeader = btoa(`${username}:${password}`);
        axiosInstance.defaults.headers.common = { Authorization: `Basic ${encodedHeader}` };

        const response = await apiCall({
            method: 'post',
            path: '/api/login',
            params: { username, password },
            axiosInstance: axiosInstance,
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
            saveLoginDetails(username, password);
            return true;
        }
    };

    const saveLoginDetails = (username: string, password: string) => {
        setUsername(username);
        setPassword(password);
    };

    return <Provider value={{ username, password, login, saveLoginDetails }}>{children}</Provider>;
};
