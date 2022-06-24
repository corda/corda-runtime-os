import { Button, Checkbox, IconButton, NotificationService, Tooltip } from '@r3/r3-tooling-design-system/exports';
import { CSSProperties, useCallback, useEffect, useState } from 'react';

import { TEMP_PARTICIPANTS } from '@/tempData/tempParticipants';
import apiCall from '@/api/apiCall';
import { axiosInstance } from '@/api/axiosConfig';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

type Props = {
    selectedParticipants: string[];
    setSelectedParticipants: (participants: string[]) => void;
    handleCloseParticipants?: () => void;
};

const ChatParticipants: React.FC<Props> = ({
    handleCloseParticipants,
    selectedParticipants,
    setSelectedParticipants,
}) => {
    const [networkParticipants, setNetworkParticipants] = useState<string[]>([]);

    const fetchNetworkParticipants = useCallback(async () => {
        // TODO: Adjust the api to spec
        const response = await apiCall({ method: 'get', path: '/api/getParticipants', axiosInstance: axiosInstance });
        if (response.error) {
            NotificationService.notify(
                `Failed to fetch network participants: Error: ${response.error}`,
                'Error',
                'danger'
            );
        } else {
            // TODO: Set participants here from api data response
        }
        // TODO: Remove this temp data
        setNetworkParticipants(TEMP_PARTICIPANTS);
    }, []);

    useEffect(() => {
        fetchNetworkParticipants();

        // TODO: set a polling interval to fetch participants if web socket implementation will not be available
    }, [fetchNetworkParticipants]);

    const handleCheckboxClicked = (checkBoxChecked: boolean, participant: string) => {
        if (!checkBoxChecked) {
            setSelectedParticipants(selectedParticipants.filter((p) => p !== participant));
        } else {
            setSelectedParticipants([...selectedParticipants, participant]);
        }
    };

    const isMobile = useMobileMediaQuery();
    const additionalStyles: CSSProperties = isMobile ? { width: '100%', maxHeight: '80%', flex: 1, padding: 6 } : {};

    return (
        <div className="pt-6 flex flex-col" style={{ width: 400, height: 450, ...additionalStyles }}>
            <div className="flex mb-2">
                <p
                    className={`ml-2 mt-auto mb-auto text-md ${
                        selectedParticipants.length === 0 ? 'text-red opacity-75' : 'text-blue'
                    }`}
                >
                    {`Selected: ${
                        selectedParticipants.length === 0
                            ? 'Please select at least one!'
                            : selectedParticipants.length === 1
                            ? selectedParticipants[0]
                            : selectedParticipants.length
                    }`}
                </p>
                <IconButton
                    className="mt-auto mb-auto ml-auto mr-4"
                    icon={'Nuke'}
                    size={'large'}
                    variant={'tertiary'}
                    disabled={selectedParticipants.length === 0}
                    onClick={() => {
                        setSelectedParticipants([]);
                    }}
                />
            </div>
            <div className="overflow-y-scroll mb-4" style={{ height: '90%' }}>
                {networkParticipants.map((nP) => {
                    const selected = selectedParticipants.includes(nP);
                    return (
                        <div className="flex gap-6">
                            <Checkbox
                                checked={selected}
                                value={nP}
                                onChange={(e) => {
                                    handleCheckboxClicked(!selected, nP);
                                    e.stopPropagation();
                                }}
                            />
                            <p
                                className={`${selected ? 'text-blue' : ''} cursor-pointer`}
                                onClick={(e) => {
                                    handleCheckboxClicked(!selected, nP);
                                    e.stopPropagation();
                                }}
                            >
                                {nP}
                            </p>
                        </div>
                    );
                })}
            </div>
            {handleCloseParticipants && (
                <Button
                    className="ml-auto mr-2"
                    iconLeft="AccountCheck"
                    size={'large'}
                    variant={'primary'}
                    onClick={handleCloseParticipants}
                >
                    Confirm
                </Button>
            )}
        </div>
    );
};

export default ChatParticipants;
