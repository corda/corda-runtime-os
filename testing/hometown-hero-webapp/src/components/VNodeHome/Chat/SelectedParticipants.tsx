import { IconButton, IconCustom } from '@r3/r3-tooling-design-system/exports';

type Props = {
    selectedParticipants: string[];
    handleClearParticipants: () => void;
    clearButtonEnabled?: boolean;
};

const SelectedParticipants: React.FC<Props> = ({
    clearButtonEnabled = true,
    selectedParticipants,
    handleClearParticipants,
}) => {
    return (
        <div className="flex mb-2">
            <div className="flex">
                <IconCustom
                    className={`w-6 ${clearButtonEnabled ? 'mt-3' : 'mt-1'} text-blue ${
                        selectedParticipants.length === 0 ? 'text-red opacity-75' : 'text-blue'
                    }`}
                    icon={'Account'}
                />
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
            </div>
            {clearButtonEnabled && (
                <IconButton
                    className="mt-auto mb-auto ml-auto mr-4"
                    icon={'Nuke'}
                    size={'large'}
                    variant={'icon'}
                    disabled={selectedParticipants.length === 0}
                    onClick={handleClearParticipants}
                />
            )}
        </div>
    );
};

export default SelectedParticipants;
