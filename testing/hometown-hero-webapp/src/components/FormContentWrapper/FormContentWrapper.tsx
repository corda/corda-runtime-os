import { useMobileMediaQuery } from '../../hooks/useMediaQueries';

type Props = {
    children?: React.ReactNode;
};

const FormContentWrapper: React.FC<Props> = ({ children }) => {
    const isMobile = useMobileMediaQuery();
    return <div className={`max-w-md ${isMobile ? 'pl-4' : 'pl-8'} pr-6 mt-6 flex flex-col gap-8`}>{children}</div>;
};

export default FormContentWrapper;
