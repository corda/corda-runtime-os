import { Button, NotificationService, PasswordInput, TextInput } from '@r3/r3-tooling-design-system/exports';

import FormContentWrapper from '../components/FormContentWrapper/FormContentWrapper';
import LoginViz from '../components/Visualizations/LoginViz';
import PageContentWrapper from '../components/PageContentWrapper/PageContentWrapper';
import PageHeader from '../components/PageHeader/PageHeader';
import VisualizationWrapper from '../components/Visualizations/VisualizationWrapper';
import apiCall from '../api/apiCall';
import { useState } from 'react';

const Login = () => {
    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');

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
        const response = await apiCall({ method: 'post', path: '/api/login', params: { username, password } });

        if (response.error) {
            NotificationService.notify(
                `Failed to Sign in with the provided username and password: Error: ${response.error}`,
                'Error',
                'danger'
            );
            setUsername('');
            setPassword('');
        } else {
            NotificationService.notify(`Successfully Signed in!`, 'Success!', 'success');

            // TODO: Update some sort of user Context state here

            // TODO: Redirect to the V-Node Home
        }
    };

    return (
        <PageContentWrapper>
            <PageHeader>Login to V-Node</PageHeader>
            <FormContentWrapper>
                <TextInput required name="username" label={'Username'} value={username} onChange={handleInputChange} />
                <PasswordInput
                    required
                    name="password"
                    label={'Password*'}
                    value={password}
                    onChange={handleInputChange}
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
