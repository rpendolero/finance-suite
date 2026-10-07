// finance-dashboard/src/vite-env.d.ts
/// <reference types="vite/client" />
import React from 'react';
import {createRoot} from 'react-dom/client';
import './styles.css';
import App from './App';

createRoot(document.getElementById('root')!).render(<React.StrictMode><App/></React.StrictMode>);