import { IconButton, IconCustom } from '@r3/r3-tooling-design-system/exports';

import style from './selectedParticipants.module.scss';

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
    const emptyParticipants = selectedParticipants.length === 0;
    return (
        <div className={style.selectedParticipantsWrapper}>
            <div className="flex">
                <IconCustom
                    className={`w-6 ${clearButtonEnabled ? 'mt-3' : 'mt-1'} ${
                        emptyParticipants ? style.error : style.blue
                    }`}
                    icon={'Account'}
                />
                <p className={`${style.text} ${emptyParticipants ? style.error : style.blue}`}>
                    {`Selected: ${
                        emptyParticipants
                            ? 'Please select at least one!'
                            : selectedParticipants.length === 1
                            ? selectedParticipants[0]
                            : selectedParticipants.length
                    }`}
                </p>
            </div>
            {clearButtonEnabled && (
                <IconButton
                    className={style.clearButton}
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
