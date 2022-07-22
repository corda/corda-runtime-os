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
    pollIntervalMs = 1000,
    onStartFailure,
    onStartSuccess,
    onStatusSuccess,
    onStatusFailure,
    auth,
}: HandleFlowParams) => {
    //console.log('START FLOW', 'Flow type: ' + flowType);
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
        flowType,
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
    flowType?: string;
};

const finishedFlowStatuses = new Set<string>();
let intervalsInProgress = 0;

export const setupFlowStatusPolling = ({
    holderShortId,
    clientRequestId = Date.now().toString(),
    pollIntervalMs = 1000,
    onStatusSuccess,
    onStatusFailure,
    onError,
    auth,
    flowType,
}: FlowStatusPollingParams) => {
    const flowPollingInterval = setInterval(async () => {
        const cleanup = () => {
            clearInterval(flowPollingInterval);
            intervalsInProgress--;
        };

        if (finishedFlowStatuses.has(clientRequestId)) {
            cleanup();
            return;
        }

        if (intervalsInProgress > 5) {
            //console.log('CANT GET FLOW STATUS' + intervalsInProgress);
            return;
        }

        intervalsInProgress++;
        //console.log('FLOW STATUS', 'Intervals In Progress: ' + intervalsInProgress);

        const response = await requestFlowStatus(holderShortId, clientRequestId, auth);
        if (response.error) {
            if (onError) {
                onError(response.error);
            }
            cleanup();
            return;
        }

        if (finishedFlowStatuses.has(clientRequestId)) {
            cleanup();
            return;
        }

        const flowStatusData: FlowStatus = response.data;

        if (flowStatusData.flowStatus !== 'START_REQUESTED' && flowStatusData.flowStatus !== 'RUNNING') {
            finishedFlowStatuses.add(clientRequestId);
        }

        if (flowStatusData.flowStatus === 'COMPLETED') {
            if (onStatusSuccess) {
                onStatusSuccess(flowStatusData.flowResult);
            }
            cleanup();
            return;
        }
        if (flowStatusData.flowStatus.includes('FAILED')) {
            if (onStatusFailure) {
                onStatusFailure(
                    `$Message: ${flowStatusData.flowError.message}, type: ${flowStatusData.flowError.type}`
                );
            }
            cleanup();
            return;
        }

        intervalsInProgress--;
    }, pollIntervalMs);

    return flowPollingInterval;
};
