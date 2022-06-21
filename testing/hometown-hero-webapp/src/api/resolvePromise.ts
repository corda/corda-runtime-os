import { AxiosResponse } from 'axios';

export type ResolvedPromise<T = any> = {
    data: T | null;
    error?: string;
    message?: string;
};

export const resolvePromise = async <T = any>(promise: Promise<AxiosResponse<T>>) => {
    try {
        const resolvedPromise = await promise;
        return { data: resolvedPromise.data };
    } catch (error: any) {
        const errorMessage = error.response?.data
            ? error.response.data.message
                ? error.response.data.message
                : error.response.data.status + ' ' + error.response.data.error
            : error;
        return {
            data: null,
            error: errorMessage,
        };
    }
};
