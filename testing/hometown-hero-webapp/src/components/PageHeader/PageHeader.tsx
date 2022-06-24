import { IconButton } from '@r3/r3-tooling-design-system/exports';
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
                    className="h-8 mt-auto mb-2 ml-2 opacity-50"
                    icon="ArrowLeft"
                    size="medium"
                    variant="secondary"
                />
            )}
            <h1 className="mt-8 ml-4 text-left w-fit text-3xl sm:text-4xl md:text-3xl lg:text-5xl">{children}</h1>
        </div>
    );
};

export default PageHeader;
