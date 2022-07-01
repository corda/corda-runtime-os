import { IconButton } from '@r3/r3-tooling-design-system/exports';
import style from './pageHeader.module.scss';
import { useNavigate } from 'react-router-dom';

type Props = {
    withBackButton?: boolean;
    children?: React.ReactNode;
};
const PageHeader: React.FC<Props> = ({ withBackButton, children }) => {
    const navigate = useNavigate();

    return (
        <div className={`flex flex-row`}>
            {withBackButton && (
                <IconButton
                    onClick={() => {
                        navigate(-1);
                    }}
                    className={style.backButton}
                    icon="ArrowLeft"
                    size="medium"
                    variant="secondary"
                />
            )}
            <h1 className={style.text}>{children}</h1>
        </div>
    );
};

export default PageHeader;
