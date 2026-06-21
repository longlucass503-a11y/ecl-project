import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import SchemeList from '../pages/scheme/SchemeList';
import SchemeOverview from '../pages/scheme/SchemeOverview';
import SchemeCompare from '../pages/scheme/SchemeCompare';
import RiskGroupConfig from '../pages/parameter/RiskGroupConfig';
import StageConfig from '../pages/parameter/StageConfig';
import PdConfig from '../pages/parameter/PdConfig';
import CcfConfig from '../pages/parameter/CcfConfig';
import LgdConfig from '../pages/parameter/LgdConfig';
import OverlayConfig from '../pages/parameter/OverlayConfig';
import TrialCenter from '../pages/trial/TrialCenter';
import JobsMonitor from '../pages/jobs/JobsMonitor';

const Placeholder: React.FC<{ title: string }> = ({ title }) => (
  <div style={{
    maxWidth: 1280, margin: '0 auto', padding: '80px 32px',
    textAlign: 'center', color: 'var(--color-text-muted)', fontSize: 14,
  }}>
    {title} — 功能开发中...
  </div>
);

const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    children: [
      { index: true, element: <Navigate to="/schemes" replace /> },
      { path: 'schemes', element: <SchemeList /> },
      { path: 'schemes/compare', element: <SchemeCompare /> },
      { path: 'schemes/:id', element: <SchemeOverview /> },
      { path: 'parameters/risk-groups', element: <RiskGroupConfig /> },
      { path: 'parameters/stage', element: <StageConfig /> },
      { path: 'parameters/pd', element: <PdConfig /> },
      { path: 'parameters/ccf', element: <CcfConfig /> },
      { path: 'parameters/lgd', element: <LgdConfig /> },
      { path: 'parameters/overlay', element: <OverlayConfig /> },
      { path: 'jobs', element: <JobsMonitor /> },
      { path: 'trial', element: <TrialCenter /> },
    ],
  },
]);

export default router;
