export type Message = {
    content: string;
    direction: 'incoming' | 'outgoing';
    id: string;
    timestamp: string;
};
