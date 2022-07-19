import axios from 'axios';

const authConfig = {
    username: 'admin',
    password: 'admin',
};
const adminAxiosInstance = axios.create({
    baseURL: 'https://localhost:8888',
    auth: authConfig,
});

export default adminAxiosInstance;
