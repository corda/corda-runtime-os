import './App.scss';

import { HOME, LOGIN } from './constants/routes';
import { Route, Routes } from 'react-router';

import { BrowserRouter } from 'react-router-dom';
import Footer from './components/Footer/Footer';
import Home from './pages/Home';
import Login from './pages/Login';
import NavBar from './components/NavBar/NavBar';

function App() {
    return (
        <div className="App">
            <BrowserRouter>
                <div className="App">
                    <NavBar />
                    <Routes>
                        <Route path={HOME} element={<Home />} />
                        <Route path={LOGIN} element={<Login />} />
                    </Routes>
                    <Footer />
                </div>
            </BrowserRouter>
        </div>
    );
}

export default App;
