import { NavLink, Outlet, Navigate } from 'react-router-dom';
import { useAuth, AUTH_STATE } from '../../contexts/AuthContext';
import Spinner from '../../components/Spinner';

/**
 * 관리자 페이지 레이아웃
 * 사이드바 네비게이션 + 메인 콘텐츠 영역
 * ADMIN 역할이 아니면 홈으로 리다이렉트
 */
export default function AdminLayout() {
  const { authState, user } = useAuth();

  // 로딩 중
  if (authState === AUTH_STATE.LOADING) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Spinner />
      </div>
    );
  }

  // 비로그인 또는 ADMIN이 아니면 홈으로 리다이렉트
  if (authState === AUTH_STATE.UNAUTHENTICATED || user?.role !== 'ADMIN') {
    return <Navigate to="/" replace />;
  }

  const navItems = [
    { to: '/admin', label: '대시보드', icon: ChartIcon, end: true },
    { to: '/admin/auctions', label: '경매 관리', icon: AuctionIcon },
    { to: '/admin/users', label: '유저 관리', icon: UserIcon },
  ];

  return (
    <div className="flex flex-col sm:flex-row min-h-[calc(100vh-64px)]">
      {/* 모바일: 상단 수평 탭 */}
      <nav className="sm:hidden flex items-center gap-1 p-3 bg-white/80 backdrop-blur-sm border-b border-gray-200/60 overflow-x-auto">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              `flex items-center gap-2 px-4 py-2 rounded-xl text-[13px] font-semibold whitespace-nowrap transition-colors ${
                isActive ? 'bg-violet-100 text-violet-700' : 'text-gray-600 hover:bg-gray-100'
              }`
            }
          >
            <item.icon className="w-4 h-4" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* PC: 사이드바 */}
      <aside className="hidden sm:block w-56 bg-white/80 backdrop-blur-sm border-r border-gray-200/60 flex-shrink-0">
        <nav className="p-4 space-y-1">
          <div className="px-3 py-2 mb-4">
            <h2 className="text-xs font-bold text-gray-400 uppercase tracking-wider">관리자</h2>
          </div>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-xl text-[13px] font-semibold transition-colors ${
                  isActive
                    ? 'bg-violet-50 text-violet-700'
                    : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                }`
              }
            >
              <item.icon className="w-5 h-5" />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      {/* 메인 콘텐츠 */}
      <main className="flex-1 p-4 sm:p-6 bg-gray-50/50">
        <Outlet />
      </main>
    </div>
  );
}

// 아이콘 컴포넌트들
function ChartIcon({ className }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
      />
    </svg>
  );
}

function AuctionIcon({ className }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
      />
    </svg>
  );
}

function UserIcon({ className }) {
  return (
    <svg className={className} fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={2}
        d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"
      />
    </svg>
  );
}
