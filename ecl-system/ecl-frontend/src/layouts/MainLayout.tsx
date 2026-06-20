import React, { useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import './MainLayout.css';
import '../components/StatusTag.css';

export interface SchemeContext {
  schemeId: string;
  schemeCode: string;
  schemeName: string;
  status: string;
}

const sidebarItems = [
  { key: 'overview', label: '方案总览', icon: '◉' },
  { key: 'risk-groups', label: '风险分组', icon: '📊' },
  { key: 'stage', label: '阶段划分', icon: '🧩' },
  { key: 'pd', label: 'PD 参数', icon: '📈' },
  { key: 'lgd', label: 'LGD 参数', icon: '📉' },
  { key: 'ccf', label: 'CCF 参数', icon: '📐' },
  { key: 'overlay', label: '管理层叠加', icon: '🛡️' },
];

const topNavItems = [
  { key: 'jobs', label: '跑批监控' },
  { key: 'schemes', label: '方案列表' },
  { key: 'trial', label: '试算中心' },
];

const MainLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [schemeContext, setSchemeContext] = useState<SchemeContext | null>(null);

  // Determine active top nav
  const pathSeg = location.pathname.split('/').filter(Boolean)[0] || '';
  const activeNav = pathSeg === 'schemes' || pathSeg === 'parameters' ? 'schemes' : pathSeg;

  // Determine active sidebar item
  const activeSp =
    location.pathname.includes('/parameters/risk-groups') ? 'risk-groups' :
    location.pathname.includes('/parameters/stage') ? 'stage' :
    location.pathname.includes('/parameters/pd') ? 'pd' :
    location.pathname.includes('/parameters/lgd') ? 'lgd' :
    location.pathname.includes('/parameters/ccf') ? 'ccf' :
    location.pathname.includes('/parameters/overlay') ? 'overlay' :
    'overview';

  const showSidebar = !!schemeContext || location.pathname.startsWith('/schemes/') || pathSeg === 'parameters';

  const handleNavClick = (key: string) => {
    if (key === 'schemes') navigate('/schemes');
    else if (key === 'jobs') navigate('/jobs');
    else if (key === 'trial') navigate('/trial');
  };

  const handleSidebarClick = (key: string) => {
    if (!schemeContext) return;
    if (key === 'overview') {
      navigate(`/schemes/${schemeContext.schemeId}`);
    } else {
      navigate(`/parameters/${key}?schemeId=${schemeContext.schemeId}`);
    }
  };

  return (
    <div className="app-shell">
      {/* Top Header */}
      <div className="top-header">
        <div className="header-inner">
          <div className="header-logo">
            ECL <span>减值计量系统</span>
          </div>
          {topNavItems.map((item) => (
            <button
              key={item.key}
              className={`nav-item ${activeNav === item.key ? 'active' : ''}`}
              onClick={() => handleNavClick(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
      </div>

      {/* Main Area */}
      <div className="main-layout">
        {/* Sidebar — visible when scheme context is active */}
        {showSidebar && (
          <div className="sidebar">
            {schemeContext ? (
              <div className="sidebar-scheme">
                <div className="ss-code">{schemeContext.schemeCode}</div>
                <div className="ss-name">{schemeContext.schemeName}</div>
                <span className={`status-tag st-${schemeContext.status.toLowerCase()}`}>
                  ● {schemeContext.status === 'EFFECTIVE' ? '已生效' :
                     schemeContext.status === 'DRAFT' ? '草稿' :
                     schemeContext.status === 'PUBLISHED' ? '已发布' : '已失效'}
                </span>
              </div>
            ) : (
              <div className="sidebar-scheme">
                <div className="ss-code" style={{ color: 'var(--color-text-muted)' }}>未选择方案</div>
                <div className="ss-name" style={{ fontSize: 13, fontWeight: 400 }}>
                  请从方案列表进入
                </div>
              </div>
            )}
            <div className="sidebar-nav">
              {sidebarItems.map((item) => (
                <button
                  key={item.key}
                  className={`sidebar-item ${activeSp === item.key ? 'active' : ''}`}
                  onClick={() => handleSidebarClick(item.key)}
                >
                  <span className="si-icon">{item.icon}</span>
                  {item.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Content */}
        <div className="content-area">
          <Outlet context={{ schemeContext, setSchemeContext }} />
        </div>
      </div>
    </div>
  );
};

export default MainLayout;
