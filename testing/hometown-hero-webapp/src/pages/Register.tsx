import { Button, Checkbox, NotificationService, PasswordInput, TextInput } from '@r3/r3-tooling-design-system/exports';

import FormContentWrapper from '@/components/FormContentWrapper/FormContentWrapper';
import { LOGIN } from '@/constants/routes';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import RegisterViz from '@/components/Visualizations/RegisterViz';
import VisualizationWrapper from '@/components/Visualizations/VisualizationWrapper';
import apiCall from '@/api/apiCall';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import useUserContext from '@/contexts/userContext';

const Register = () => {
    const { saveLoginDetails } = useUserContext();

    const navigate = useNavigate();

    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [confirmPassword, setConfirmPassword] = useState<string>('');
    const [isUserAndPasswordSaved, setIsUserAndPasswordSaved] = useState<boolean>(true);

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
        //the api spec is not made available yet will just assume it will just assume its /api/register for now
        // TODO: update api to match spec
        const response = await apiCall({ method: 'post', path: '/api/register', params: { username, password } });

        if (response.error) {
            NotificationService.notify(
                `Failed to register with the provided username and password: Error: ${response.error}`,
                'Error',
                'danger'
            );
            setUsername('');
            setPassword('');
            setConfirmPassword('');
        } else {
            NotificationService.notify(`Successfully registered!`, 'Success!', 'success');

            if (isUserAndPasswordSaved) {
                saveLoginDetails(username, password);
            }

            navigate(LOGIN);
        }
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
                    onClick={handleSubmit}
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
