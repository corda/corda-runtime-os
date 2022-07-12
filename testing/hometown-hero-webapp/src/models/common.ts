export type FlowResult = {
    gameStatus: string;
    player1X500Name: string;
    player2X500Name: string;
    nextPlayersTurn: number;
    boardState: number[][];
};

export type FlowStatus = {
    clientRequestId: string;
    flowError: string;
    flowId: string;
    flowResult: string;
    flowStatus: string;
    holdingShortId: string;
    timestamp: string;
};

export type PollRequest = {
    requestId: string;
    flowStatus: FlowStatus;
};

export type NodeDetails = {
    playerName: string;
    shortId: string;
};
