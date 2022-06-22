import { Button, NotificationService, PasswordInput, TextInput } from '@r3/r3-tooling-design-system/exports';

import FormContentWrapper from '../components/FormContentWrapper/FormContentWrapper';
import LoginViz from '../components/Visualizations/LoginViz';
import PageContentWrapper from '../components/PageContentWrapper/PageContentWrapper';
import PageHeader from '../components/PageHeader/PageHeader';
import { VNODE_HOME } from '../constants/routes';
import VisualizationWrapper from '../components/Visualizations/VisualizationWrapper';
import apiCall from '../api/apiCall';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import useUserContext from '../contexts/userContext';

const Login = () => {
    const { username: savedUsername, password: savedPassword, login } = useUserContext();

    const navigate = useNavigate();

    const [username, setUsername] = useState<string>(savedUsername);
    const [password, setPassword] = useState<string>(savedPassword);

    const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = event.target;
        if (name === 'username') {
            setUsername(value);
        }
        if (name === 'password') {
            setPassword(value);
        }
    };

    const handleSubmit = async () => {
        //the api spec is not made available yet will just assume it will just assume its /api/login for now
        // TODO: update api to match spec

        const loggedInSuccessfully = await login(username, password);

        if (!loggedInSuccessfully) {
            setUsername('');
            setPassword('');
        } else {
            NotificationService.notify(`Successfully Signed in!`, 'Success!', 'success');
            navigate(VNODE_HOME);
        }
    };

    return (
        <PageContentWrapper>
            <PageHeader lowerOnMobile={false} withBackButton>
                Login to V-Node
            </PageHeader>
            <FormContentWrapper>
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
                <Button
                    className="h-12 w-32"
                    size={'large'}
                    variant={'primary'}
                    disabled={username.length === 0 || password.length === 0}
                    onClick={handleSubmit}
                >
                    Sign In
                </Button>
            </FormContentWrapper>
            <VisualizationWrapper width={700}>
                <LoginViz />
            </VisualizationWrapper>
        </PageContentWrapper>
    );
};

export default Login;
