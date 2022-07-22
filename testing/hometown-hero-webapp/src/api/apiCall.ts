import { ResolvedPromise, resolvePromise } from './resolvePromise';
import axios, { AxiosInstance, AxiosRequestConfig, AxiosStatic, CancelToken } from 'axios';

import { BASE_URL } from '@/constants/baseUrl';
import { trackPromise } from 'react-promise-tracker';

//Globally tracking all api calls with react-promise-tracker

export type ApiCallParams = {
    method: 'get' | 'post' | 'put';
    baseUrl?: string;
    headers?: any;
    path: string;
    dontTrackRequest?: boolean;
    params?: any;
    config?: any;
    cancelToken?: CancelToken;
    axiosInstance?: AxiosInstance;
    auth?: { username: string; password: string };
};

export default async function apiCall({
    method,
    baseUrl,
    path,
    dontTrackRequest,
    params,
    cancelToken,
    config,
    axiosInstance,
    auth,
    headers,
}: ApiCallParams): Promise<ResolvedPromise> {
    const parameters = method === 'get' ? { params } : { data: params };
    const requestConfig: AxiosRequestConfig = {
        baseURL: baseUrl ?? BASE_URL,
        url: `${path}`,
        method,
        cancelToken: cancelToken,
        auth: auth,
        ...config,
        ...parameters,
        ...headers,
    };
    const axiosHandler: AxiosInstance | AxiosStatic = axiosInstance ?? axios;

    return dontTrackRequest
        ? await resolvePromise(axiosHandler(requestConfig))
        : await resolvePromise(trackPromise(axiosHandler(requestConfig)));
}
