import { IconButton } from '@r3/r3-tooling-design-system/exports';
import { useMobileMediaQuery } from '../../hooks/useMediaQueries';
import { useNavigate } from 'react-router-dom';

type Props = {
    withBackButton?: boolean;
    children?: React.ReactNode;
};
const PageHeader: React.FC<Props> = ({ withBackButton, children }) => {
    const navigate = useNavigate();
    const isMobile = useMobileMediaQuery();
    return (
        <div className="flex flex-row">
            {withBackButton && (
                <IconButton
                    onClick={() => {
                        navigate(-1);
                    }}
                    className="h-8 mt-auto mb-2 -ml-4"
                    style={{ opacity: 0.5 }}
                    icon="ArrowLeft"
                    size="medium"
                    variant="secondary"
                />
            )}
            <h1 className="mt-8 ml-4 text-left w-fit" style={{ opacity: 0.9, fontSize: isMobile ? 34 : undefined }}>
                {children}
            </h1>
        </div>
    );
};

export default PageHeader;
