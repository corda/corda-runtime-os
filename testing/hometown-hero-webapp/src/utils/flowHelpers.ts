import { FlowTypes, requestFlowStatus, requestStartFlow } from '@/api/flows';

import { FlowStatus } from '@/models/common';

type HandleFlowParams = {
    flowType: FlowTypes;
    holderShortId: string;
    payload?: any;
    clientRequestId?: string;
    pollIntervalMs?: number;
    onStartFailure?: (errorText: string) => void;
    onStartSuccess?: (data: any) => void;
    onStatusFailure?: (errorText: string) => void;
    onStatusSuccess?: (flowResult: string) => void;
    auth?: { username: string; password: string };
};

export const handleFlow = async ({
    flowType,
    holderShortId,
    payload,
    clientRequestId = Date.now().toString(),
    pollIntervalMs = 500,
    onStartFailure,
    onStartSuccess,
    onStatusSuccess,
    onStatusFailure,
    auth,
}: HandleFlowParams) => {
    const response = await requestStartFlow(holderShortId, clientRequestId, flowType, payload, auth);
    if (response.error) {
        if (onStartFailure) {
            onStartFailure(response.error);
        }
    } else {
        if (onStartSuccess) {
            onStartSuccess(response.data);
        }
    }
    return setupFlowStatusPolling({
        holderShortId,
        clientRequestId,
        pollIntervalMs,
        onStatusSuccess,
        onStatusFailure,
        auth,
    });
};

type FlowStatusPollingParams = {
    holderShortId: string;
    clientRequestId?: string;
    pollIntervalMs?: number;
    onStatusFailure?: (errorText: string) => void;
    onStatusSuccess?: (flowResult: string) => void;
    onError?: (errorText: string) => void;
    auth?: { username: string; password: string };
};

export const setupFlowStatusPolling = ({
    holderShortId,
    clientRequestId = Date.now().toString(),
    pollIntervalMs = 500,
    onStatusSuccess,
    onStatusFailure,
    onError,
    auth,
}: FlowStatusPollingParams) => {
    const flowPollingInterval = setInterval(async () => {
        const response = await requestFlowStatus(holderShortId, clientRequestId, auth);
        if (response.error) {
            if (onError) {
                onError(response.error);
            }
            clearInterval(flowPollingInterval);
        }

        const flowStatusData: FlowStatus = response.data;

        if (flowStatusData.flowStatus === 'COMPLETED') {
            if (onStatusSuccess) {
                onStatusSuccess(flowStatusData.flowResult);
            }
            clearInterval(flowPollingInterval);
        }

        if (flowStatusData.flowStatus === 'FAILED') {
            if (onStatusFailure) {
                onStatusFailure(flowStatusData.flowResult);
            }
            clearInterval(flowPollingInterval);
        }
    }, pollIntervalMs);

    return flowPollingInterval;
};
