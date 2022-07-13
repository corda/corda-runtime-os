import { ResolvedPromise, resolvePromise } from './resolvePromise';
import axios, { AxiosInstance, AxiosRequestConfig, AxiosStatic, CancelToken } from 'axios';

import { trackPromise } from 'react-promise-tracker';

//Globally tracking all api calls with react-promise-tracker

export type ApiCallParams = {
    method: 'get' | 'post' | 'put';
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
    path,
    dontTrackRequest,
    params,
    cancelToken,
    config,
    axiosInstance,
    auth,
}: ApiCallParams): Promise<ResolvedPromise> {
    const parameters = method === 'get' ? { params } : { data: params };
    const requestConfig: AxiosRequestConfig = {
        baseURL: 'https://localhost:8888',
        url: `${path}`,
        method,
        cancelToken: cancelToken,
        auth: auth,
        ...config,
        ...parameters,
    };
    const axiosHandler: AxiosInstance | AxiosStatic = axiosInstance ?? axios;

    return dontTrackRequest
        ? await resolvePromise(axiosHandler(requestConfig))
        : await resolvePromise(trackPromise(axiosHandler(requestConfig)));
}
