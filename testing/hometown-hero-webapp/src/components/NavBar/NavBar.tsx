import { IconButton, TopNavBar } from '@r3/r3-tooling-design-system/exports';

import { VNODE_HOME } from '@/constants/routes';
import logoSrc from '@r3/r3-tooling-design-system/assets/img/logo--r3.svg';
import { useLocation } from 'react-router-dom';

const LOGO = (
    <a href="/">
        <img alt="r3-logo" src={logoSrc} width="40px" />
    </a>
);

const APP_TITLE = 'Hometown Hero Demo';

const NavBar = () => {
    const location = useLocation();
    document.title = APP_TITLE;
    return (
        <TopNavBar
            className="flex-shrink-1 sm:flex-shrink-0 md:flex-shrink-0 lg:flex-shrink-0"
            logo={LOGO}
            title={APP_TITLE}
            center={
                location.pathname.includes(VNODE_HOME) ? (
                    <div>
                        <IconButton icon={'ExitToApp'} size={'large'} variant={'icon'} />
                    </div>
                ) : undefined
            }
            centerPos={'end'}
        />
    );
};

export default NavBar;
