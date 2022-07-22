import { Button, Checkbox } from '@r3/r3-tooling-design-system/exports';
import { memo, useEffect, useMemo, useState } from 'react';

import SelectedParticipants from '../SelectedParticipants/SelectedParticipants';
import style from './chatParticipants.module.scss';
import useAppDataContext from '@/contexts/appDataContext';
import useMessagesContext from '@/contexts/messagesContext';
import useUserContext from '@/contexts/userContext';

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
    const { vNodes, refreshVNodes } = useAppDataContext();
    const { vNode: myVNode } = useUserContext();
    const { getTotalMessagesForCounterparty, fetchMessages } = useMessagesContext();
    const [refreshCounter, setRefreshCounter] = useState<number>(0);

    useEffect(() => {
        fetchMessages();

        const bumpCounterInterval = setInterval(() => {
            setRefreshCounter((prev) => prev + 1);
        }, 1000);

        return () => {
            clearInterval(bumpCounterInterval);
        };
    }, []);

    const handleCheckboxClicked = (checkBoxChecked: boolean, participant: string) => {
        if (!checkBoxChecked) {
            setSelectedParticipants(selectedParticipants.filter((p) => p !== participant));
        } else {
            //allows for multiple participant selection
            //setSelectedParticipants([...selectedParticipants, participant]);
            setSelectedParticipants([participant]);
        }
    };

    const networkParticipants = useMemo(
        () =>
            vNodes
                .map((node) => node.holdingIdentity.x500Name)
                .filter((x500) => x500 !== myVNode?.holdingIdentity.x500Name)
                .sort((a, b) => {
                    const aMessages = getTotalMessagesForCounterparty(a);
                    const bMessages = getTotalMessagesForCounterparty(b);

                    if (aMessages > bMessages) return -1;

                    if (aMessages < bMessages) return 1;

                    return 0;
                }),
        [vNodes, myVNode, refreshCounter]
    );

    return (
        <div className={style.chatParticipants}>
            <SelectedParticipants
                selectedParticipants={selectedParticipants}
                handleClearParticipants={() => {
                    setSelectedParticipants([]);
                }}
            />
            <div className={style.participantsWrapper}>
                {networkParticipants.map((nP) => {
                    const selected = selectedParticipants.includes(nP);
                    return (
                        <div className={style.participantContainer} key={nP}>
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

                            <p className="ml-auto mr-5 text-lg">
                                <strong>{getTotalMessagesForCounterparty(nP)}</strong>
                            </p>
                        </div>
                    );
                })}
            </div>
            {handleCloseParticipants && (
                <Button
                    className={style.confirmParticipants}
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

export default memo(ChatParticipants);
