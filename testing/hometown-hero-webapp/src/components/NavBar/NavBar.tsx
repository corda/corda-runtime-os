import { TopNavBar } from '@r3/r3-tooling-design-system/exports';
import logoSrc from '@r3/r3-tooling-design-system/assets/img/logo--r3.svg';

const LOGO = (
    <a href="/">
        <img alt="r3-logo" style={{ opacity: 0.8 }} src={logoSrc} width="40px" />
    </a>
);

const APP_TITLE = 'Hometown Hero Demo';

const NavBar = () => {
    document.title = APP_TITLE;
    return <TopNavBar logo={LOGO} title={APP_TITLE} />;
};

export default NavBar;
