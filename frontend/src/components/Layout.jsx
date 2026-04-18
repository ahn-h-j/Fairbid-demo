import { Link, Outlet, useLocation, Navigate } from 'react-router-dom';
import { useAuth, AUTH_STATE } from '../contexts/AuthContext';
import UserDropdown from './UserDropdown';
import NotificationDropdown from './NotificationDropdown';

/**
 * 앱 전체 레이아웃 컴포넌트
 * 글래스 모피즘 헤더 + 그라데이션 배경 + 메인 콘텐츠 영역
 * 인증 상태에 따라 헤더 우측 UI가 변경된다.
 * 온보딩 미완료 시 /onboarding 외 페이지 접근을 차단한다.
 */
export default function Layout() {
  const location = useLocation();
  const { authState, user } = useAuth();

  const isActive = (path) => location.pathname === path;

  const isLoggedIn =
    authState === AUTH_STATE.AUTHENTICATED || authState === AUTH_STATE.ONBOARDING_REQUIRED;

  // 온보딩 필요 상태인데 /onboarding이 아니면 강제 리다이렉트
  const isOnboardingRequired = authState === AUTH_STATE.ONBOARDING_REQUIRED;
  if (isOnboardingRequired && location.pathname !== '/onboarding') {
    return <Navigate to="/onboarding" replace />;
  }

  return (
    <div className="min-h-screen bg-[#f8fafc]">
      {/* 배경 장식 */}
      <div className="fixed inset-0 -z-10 overflow-hidden pointer-events-none">
        <div className="absolute -top-40 -right-40 w-[500px] h-[500px] bg-gradient-to-br from-blue-100/40 to-violet-100/40 rounded-full blur-3xl" />
        <div className="absolute top-1/3 -left-40 w-[400px] h-[400px] bg-gradient-to-br from-blue-50/50 to-cyan-50/50 rounded-full blur-3xl" />
      </div>

      {/* 헤더 */}
      <header
        className="sticky top-0 z-50 glass border-b border-white/60"
        style={{ paddingTop: 'env(safe-area-inset-top, 0px)' }}
      >
        <div
          className="max-w-6xl mx-auto px-5 sm:px-8"
          style={{
            paddingLeft: 'max(1.25rem, env(safe-area-inset-left, 0px))',
            paddingRight: 'max(1.25rem, env(safe-area-inset-right, 0px))',
          }}
        >
          <div className="flex items-center justify-between h-[56px] sm:h-[64px]">
            {/* 로고 */}
            <Link to="/auctions" className="flex items-center gap-3 group">
              <div className="relative w-9 h-9 bg-gradient-to-br from-blue-500 to-violet-600 rounded-xl flex items-center justify-center shadow-lg shadow-blue-500/25 group-hover:shadow-blue-500/40 transition-shadow duration-300">
                <svg
                  className="w-[18px] h-[18px] text-white"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2.5}
                    d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
                  />
                </svg>
              </div>
              <div className="flex flex-col">
                <span className="text-[17px] font-extrabold gradient-text leading-tight tracking-tight">
                  FairBid
                </span>
                <span className="hidden sm:block text-[10px] text-gray-400 font-medium tracking-wide">
                  호구 없는 경매
                </span>
              </div>
            </Link>

            {/* 네비게이션 + 인증 영역 (오른쪽) */}
            <div className="flex items-center">
              {/* 네비게이션: 온보딩 중에는 숨김, 모바일에서는 하단 탭 바 사용 */}
              {!isOnboardingRequired && (
                <nav className="hidden sm:flex items-center gap-1">
                  <Link
                    to="/auctions"
                    className={`relative text-[13px] font-semibold px-4 py-2 rounded-xl transition-colors duration-200 ${
                      isActive('/auctions')
                        ? 'text-blue-600 bg-blue-50/80'
                        : 'text-gray-500 hover:text-gray-900 hover:bg-gray-100/60'
                    }`}
                  >
                    {isActive('/auctions') && (
                      <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 w-4 h-0.5 bg-blue-500 rounded-full" />
                    )}
                    <span className="hidden sm:inline">경매 </span>목록
                  </Link>
                  <Link
                    to="/auctions/create"
                    className={`relative text-[13px] font-semibold px-4 py-2 rounded-xl transition-colors duration-200 ${
                      isActive('/auctions/create')
                        ? 'text-blue-600 bg-blue-50/80'
                        : 'text-gray-500 hover:text-gray-900 hover:bg-gray-100/60'
                    }`}
                  >
                    {isActive('/auctions/create') && (
                      <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 w-4 h-0.5 bg-blue-500 rounded-full" />
                    )}
                    <span className="hidden sm:inline">경매 </span>등록
                  </Link>

                  {/* 내 거래 링크: 로그인 시 노출 */}
                  {isLoggedIn && (
                    <Link
                      to="/trades"
                      className={`relative text-[13px] font-semibold px-4 py-2 rounded-xl transition-colors duration-200 ${
                        location.pathname.startsWith('/trades')
                          ? 'text-blue-600 bg-blue-50/80'
                          : 'text-gray-500 hover:text-gray-900 hover:bg-gray-100/60'
                      }`}
                    >
                      {location.pathname.startsWith('/trades') && (
                        <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 w-4 h-0.5 bg-blue-500 rounded-full" />
                      )}
                      <span className="hidden sm:inline">내 </span>거래
                    </Link>
                  )}

                  {/* 관리자 링크: ADMIN 역할일 때만 노출 */}
                  {user?.role === 'ADMIN' && (
                    <Link
                      to="/admin"
                      className={`relative text-[13px] font-semibold px-4 py-2 rounded-xl transition-colors duration-200 ${
                        location.pathname.startsWith('/admin')
                          ? 'text-violet-600 bg-violet-50/80'
                          : 'text-gray-500 hover:text-gray-900 hover:bg-gray-100/60'
                      }`}
                    >
                      {location.pathname.startsWith('/admin') && (
                        <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 w-4 h-0.5 bg-violet-500 rounded-full" />
                      )}
                      관리자
                    </Link>
                  )}
                </nav>
              )}

              {/* 인증 영역: 모바일/PC 모두 표시 */}
              {!isOnboardingRequired && (
                <div className="flex items-center gap-2 sm:border-l sm:border-gray-200/60 sm:pl-3 sm:ml-2">
                  {/* 로딩 중에는 빈 공간 (깜빡임 방지) */}
                  {authState === AUTH_STATE.LOADING && <div className="w-8 h-8" />}
                  {/* 로그인 상태: 알림 (+ PC에서만 닉네임 드롭다운) */}
                  {authState !== AUTH_STATE.LOADING && isLoggedIn && (
                    <>
                      <NotificationDropdown />
                      <div className="hidden sm:block">
                        <UserDropdown />
                      </div>
                    </>
                  )}
                  {/* 비로그인 상태: 로그인 버튼 (PC에서만) */}
                  {authState !== AUTH_STATE.LOADING && !isLoggedIn && (
                    <Link
                      to="/login"
                      state={{ from: location.pathname + location.search + location.hash }}
                      className="hidden sm:block text-[13px] font-semibold px-4 py-2 text-white bg-gradient-to-r from-blue-500 to-violet-600 rounded-xl shadow-sm shadow-blue-500/20 hover:shadow-blue-500/40 transition-shadow duration-200"
                    >
                      로그인
                    </Link>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* 메인 콘텐츠 */}
      <main className="max-w-6xl mx-auto px-5 sm:px-8 py-8 pb-24 sm:pb-8">
        <Outlet />
      </main>

      {/* 모바일 하단 탭 바: 온보딩 중에는 숨김 */}
      {!isOnboardingRequired && (
        <nav
          className="fixed bottom-0 left-0 right-0 z-50 sm:hidden glass border-t border-white/60"
          style={{ paddingBottom: 'env(safe-area-inset-bottom, 0px)' }}
        >
          <div className="flex items-center justify-around h-16 px-2">
            {/* 경매 목록 */}
            <Link
              to="/auctions"
              className={`flex flex-col items-center justify-center flex-1 py-2 ${
                isActive('/auctions') ? 'text-blue-600' : 'text-gray-400'
              }`}
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
                />
              </svg>
              <span className="text-[10px] font-medium mt-1">경매</span>
            </Link>

            {/* 등록 */}
            <Link
              to="/auctions/create"
              className={`flex flex-col items-center justify-center flex-1 py-2 ${
                isActive('/auctions/create') ? 'text-blue-600' : 'text-gray-400'
              }`}
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M12 4v16m8-8H4"
                />
              </svg>
              <span className="text-[10px] font-medium mt-1">등록</span>
            </Link>

            {/* 거래 (로그인 시) */}
            {isLoggedIn && (
              <Link
                to="/trades"
                className={`flex flex-col items-center justify-center flex-1 py-2 ${
                  location.pathname.startsWith('/trades') ? 'text-blue-600' : 'text-gray-400'
                }`}
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"
                  />
                </svg>
                <span className="text-[10px] font-medium mt-1">거래</span>
              </Link>
            )}

            {/* 관리자 (ADMIN일 때) */}
            {user?.role === 'ADMIN' && (
              <Link
                to="/admin"
                className={`flex flex-col items-center justify-center flex-1 py-2 ${
                  location.pathname.startsWith('/admin') ? 'text-violet-600' : 'text-gray-400'
                }`}
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"
                  />
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"
                  />
                </svg>
                <span className="text-[10px] font-medium mt-1">관리</span>
              </Link>
            )}

            {/* 마이페이지 / 로그인 */}
            {isLoggedIn ? (
              <Link
                to="/mypage"
                className={`flex flex-col items-center justify-center flex-1 py-2 ${
                  isActive('/mypage') ? 'text-blue-600' : 'text-gray-400'
                }`}
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"
                  />
                </svg>
                <span className="text-[10px] font-medium mt-1">MY</span>
              </Link>
            ) : (
              <Link
                to="/login"
                state={{ from: location.pathname + location.search + location.hash }}
                className="flex flex-col items-center justify-center flex-1 py-2 text-gray-400"
              >
                <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1"
                  />
                </svg>
                <span className="text-[10px] font-medium mt-1">로그인</span>
              </Link>
            )}
          </div>
        </nav>
      )}
    </div>
  );
}
