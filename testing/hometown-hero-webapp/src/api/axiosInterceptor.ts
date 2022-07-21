import axios from 'axios';

const MAX_REQUESTS_COUNT = 5;
const INTERVAL_MS = 500;
let PENDING_REQUESTS = 0;
// create new axios instance
const api = axios.create({});
/**
 * Axios Request Interceptor
 */
api.interceptors.request.use(function (config) {
    return new Promise((resolve, reject) => {
        let interval = setInterval(() => {
            if (PENDING_REQUESTS < MAX_REQUESTS_COUNT) {
                PENDING_REQUESTS++;
                clearInterval(interval);
                resolve(config);
            }
        }, INTERVAL_MS);
    });
});
/**
 * Axios Response Interceptor
 */
api.interceptors.response.use(
    function (response) {
        PENDING_REQUESTS = Math.max(0, PENDING_REQUESTS - 1);
        return Promise.resolve(response);
    },
    function (error) {
        PENDING_REQUESTS = Math.max(0, PENDING_REQUESTS - 1);
        return Promise.reject(error);
    }
);
export default api;
