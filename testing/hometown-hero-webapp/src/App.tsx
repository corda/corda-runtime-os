import './App.scss';

import { Route, Routes } from 'react-router';

import { BrowserRouter } from 'react-router-dom';
import Footer from './components/Footer/Footer';
import Home from './pages/Home';
import NavBar from './components/NavBar/NavBar';

function App() {
    return (
        <div className="App">
            <BrowserRouter>
                <div className="App">
                    <NavBar />
                    <Routes>
                        <Route path="/" element={<Home />} />
                    </Routes>
                    <Footer />
                </div>
            </BrowserRouter>
        </div>
    );
}

export default App;
