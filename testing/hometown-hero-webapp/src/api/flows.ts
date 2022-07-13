import apiCall from './apiCall';

export type FlowTypes = 'net.cordapp.testing.chat.ChatOutgoingFlow' | 'net.cordapp.testing.chat.ChatReaderFlow';

export const requestStartFlow = async (
    holderShortId: string,
    clientRequestId: string,
    flowType: FlowTypes,
    payload?: any,
    auth?: { username: string; password: string }
) => {
    return apiCall({
        method: 'post',
        path: `/api/v1/flow/${holderShortId}`,
        params: {
            httpStartFlow: {
                clientRequestId: clientRequestId,
                flowClassName: flowType,
                requestData: payload,
            },
        },
        auth: auth,
    });
};

export const requestFlowStatus = async (
    shortId: string,
    clientRequestId: string,
    auth?: { username: string; password: string }
) => {
    return apiCall({ method: 'get', path: `/api/v1/flow/${shortId}/${clientRequestId}`, auth: auth });
};
