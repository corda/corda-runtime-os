import { Checkbox, NotificationService } from '@r3/r3-tooling-design-system/exports';
import { useCallback, useEffect, useState } from 'react';

import { TEMP_PARTICIPANTS } from '@/tempData/tempParticipants';
import apiCall from '@/api/apiCall';
import { axiosInstance } from '@/api/axiosConfig';

type Props = {
    selectedParticipants: string[];
    setSelectedParticipants: (participants: string[]) => void;
};

const ChatParticipants: React.FC<Props> = ({ selectedParticipants, setSelectedParticipants }) => {
    const [networkParticipants, setNetworkParticipants] = useState<string[]>([]);

    const fetchNetworkParticipants = useCallback(async () => {
        //fetch participants here with some api call
        const response = await apiCall({ method: 'get', path: '/api/getParticipants', axiosInstance: axiosInstance });
        if (response.error) {
            NotificationService.notify(
                `Failed to fetch network participants: Error: ${response.error}`,
                'Error',
                'danger'
            );
        } else {
            //Set participants here
        }
        setNetworkParticipants(TEMP_PARTICIPANTS);
    }, []);

    useEffect(() => {
        fetchNetworkParticipants();
    }, []);

    const handleCheckboxClicked = (checkBoxChecked: boolean, participant: string) => {
        if (!checkBoxChecked) {
            setSelectedParticipants(selectedParticipants.filter((p) => p !== participant));
        } else {
            setSelectedParticipants([...selectedParticipants, participant]);
        }
    };

    return (
        <div className="pt-6" style={{ width: 380, height: 550 }}>
            <p
                className={`mb-4 ml-2 text-xl ${
                    selectedParticipants.length === 0 ? 'text-red opacity-75' : 'text-blue'
                }`}
            >
                {`Selected: ${
                    selectedParticipants.length === 0 ? 'Please select at least one!' : selectedParticipants.length
                }`}
            </p>
            <div className="overflow-y-scroll" style={{ height: 450 }}>
                {networkParticipants.map((nP) => {
                    const selected = selectedParticipants.includes(nP);
                    return (
                        <div className="flex gap-6">
                            <Checkbox
                                value={nP}
                                onChange={(e) => {
                                    handleCheckboxClicked(e.target.checked, nP);
                                }}
                            />
                            <p className={`${selected ? 'text-blue' : ''}`}>{nP}</p>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default ChatParticipants;
