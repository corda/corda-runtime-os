import { IconButton, IconCustom } from '@r3/r3-tooling-design-system/exports';

import style from './selectedParticipants.module.scss';
import { useNavigate } from 'react-router-dom';

type Props = {
    selectedParticipants: string[];
    handleClearParticipants: () => void;
    onClick?: () => void;
};

const SelectedParticipants: React.FC<Props> = ({ selectedParticipants, handleClearParticipants, onClick }) => {
    const emptyParticipants = selectedParticipants.length === 0;

    return (
        <div className={style.selectedParticipantsWrapper}>
            <div className="flex" onClick={onClick}>
                <IconCustom
                    className={`w-6 h-10 ${emptyParticipants ? style.error : style.blue} pt-1`}
                    icon={'Account'}
                />
                <p className={`${style.text} ${emptyParticipants ? style.error : style.blue}`}>
                    {`${
                        emptyParticipants
                            ? 'Please select a participant!'
                            : selectedParticipants.length === 1
                            ? selectedParticipants[0]
                            : selectedParticipants.length
                    }`}
                </p>
            </div>
        </div>
    );
};

export default SelectedParticipants;
