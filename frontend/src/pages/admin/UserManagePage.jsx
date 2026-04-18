import { useState, useEffect } from 'react';
import { getAdminUserList } from '../../api/admin';
import LoadingSpinner from '../../components/LoadingSpinner';

/**
 * 유저 관리 페이지
 * 유저 목록 조회 및 상태 확인
 */
export default function UserManagePage() {
  // 검색 상태
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');

  // 페이지네이션 상태
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);

  // 로딩 및 에러 상태
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 데이터 로드
  useEffect(() => {
    async function fetchUsers() {
      setLoading(true);
      setError(null);

      try {
        const data = await getAdminUserList({
          keyword: searchKeyword || null,
          page,
          size: 15,
        });
        setPageData(data);
      } catch (err) {
        console.error('유저 목록 조회 실패:', err);
        setError(err.message || '유저 목록을 불러오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    }

    fetchUsers();
  }, [searchKeyword, page]);

  // 검색 핸들러
  const handleSearch = (e) => {
    e.preventDefault();
    setSearchKeyword(keyword);
    setPage(0);
  };

  const users = pageData?.content || [];
  const totalPages = pageData?.totalPages || 0;
  const totalElements = pageData?.totalElements || 0;

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">유저 관리</h1>
        <p className="text-sm text-gray-500 mt-1">총 {totalElements.toLocaleString()}명의 유저</p>
      </div>

      {/* 검색 */}
      <form onSubmit={handleSearch} className="flex flex-col sm:flex-row gap-2 sm:gap-3">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="닉네임 또는 이메일 검색..."
          className="flex-1 px-4 py-2.5 bg-white rounded-xl ring-1 ring-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <button
          type="submit"
          className="px-5 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors"
        >
          검색
        </button>
      </form>

      {/* 에러 표시 */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
        </div>
      )}

      {/* 모바일: 카드 리스트 */}
      <div className="sm:hidden space-y-3">
        {loading && (
          <div className="py-12 text-center">
            <LoadingSpinner />
          </div>
        )}
        {!loading && users.length === 0 && (
          <div className="py-12 text-center text-gray-400 text-sm">유저가 없습니다</div>
        )}
        {!loading &&
          users.length > 0 &&
          users.map((user) => (
            <div key={user.id} className="bg-white rounded-xl p-4 ring-1 ring-gray-100 shadow-sm">
              <div className="flex items-start justify-between gap-3 mb-2">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-gray-900">
                      {user.nickname || '-'}
                    </span>
                    {!user.isOnboarded && (
                      <span className="text-xs px-1.5 py-0.5 bg-yellow-100 text-yellow-700 rounded">
                        미완료
                      </span>
                    )}
                  </div>
                  <p className="text-xs text-gray-500 mt-0.5 truncate">
                    #{user.id} · {user.email}
                  </p>
                </div>
                <StatusBadge isBlocked={user.isBlocked} isActive={user.isActive} />
              </div>
              <div className="flex items-center gap-2 text-xs">
                <ProviderBadge provider={user.provider} />
                <RoleBadge role={user.role} />
                {user.warningCount > 0 && (
                  <span className="text-red-600 font-semibold">경고 {user.warningCount}/3</span>
                )}
                <span className="text-gray-400 ml-auto">{formatDate(user.createdAt)}</span>
              </div>
            </div>
          ))}
      </div>

      {/* PC: 테이블 */}
      <div className="hidden sm:block bg-white rounded-2xl shadow-sm ring-1 ring-gray-100 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">
                  ID
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">
                  닉네임
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">
                  이메일
                </th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">
                  Provider
                </th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">
                  역할
                </th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">
                  경고
                </th>
                <th className="px-4 py-3 text-center text-xs font-semibold text-gray-500 uppercase">
                  상태
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase">
                  가입일
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center">
                    <LoadingSpinner />
                  </td>
                </tr>
              )}
              {!loading && users.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-gray-400 text-sm">
                    유저가 없습니다
                  </td>
                </tr>
              )}
              {!loading &&
                users.length > 0 &&
                users.map((user) => (
                  <tr key={user.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm text-gray-500">{user.id}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-gray-900">
                          {user.nickname || '-'}
                        </span>
                        {!user.isOnboarded && (
                          <span className="text-xs px-1.5 py-0.5 bg-yellow-100 text-yellow-700 rounded">
                            미완료
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{user.email}</td>
                    <td className="px-4 py-3 text-center">
                      <ProviderBadge provider={user.provider} />
                    </td>
                    <td className="px-4 py-3 text-center">
                      <RoleBadge role={user.role} />
                    </td>
                    <td className="px-4 py-3 text-center">
                      {user.warningCount > 0 ? (
                        <span className="text-red-600 font-semibold">{user.warningCount}/3</span>
                      ) : (
                        <span className="text-gray-400">0</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-center">
                      <StatusBadge isBlocked={user.isBlocked} isActive={user.isActive} />
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {formatDate(user.createdAt)}
                    </td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 p-4 border-t border-gray-100">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              이전
            </button>
            <span className="text-sm text-gray-600">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              다음
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Provider 뱃지 컴포넌트
 */
function ProviderBadge({ provider }) {
  const config = {
    KAKAO: { label: 'Kakao', color: 'bg-yellow-100 text-yellow-800' },
    NAVER: { label: 'Naver', color: 'bg-green-100 text-green-800' },
    GOOGLE: { label: 'Google', color: 'bg-blue-100 text-blue-800' },
  };

  const { label, color } = config[provider] || {
    label: provider,
    color: 'bg-gray-100 text-gray-700',
  };

  return (
    <span className={`inline-flex px-2 py-0.5 rounded text-xs font-medium ${color}`}>{label}</span>
  );
}

/**
 * 역할 뱃지 컴포넌트
 */
function RoleBadge({ role }) {
  if (role === 'ADMIN') {
    return (
      <span className="inline-flex px-2 py-0.5 rounded text-xs font-medium bg-purple-100 text-purple-800">
        ADMIN
      </span>
    );
  }
  return (
    <span className="inline-flex px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600">
      USER
    </span>
  );
}

/**
 * 상태 뱃지 컴포넌트
 */
function StatusBadge({ isBlocked, isActive }) {
  if (isBlocked) {
    return (
      <span className="inline-flex px-2.5 py-1 rounded-full text-xs font-medium bg-red-100 text-red-700">
        차단됨
      </span>
    );
  }
  if (!isActive) {
    return (
      <span className="inline-flex px-2.5 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-700">
        비활성
      </span>
    );
  }
  return (
    <span className="inline-flex px-2.5 py-1 rounded-full text-xs font-medium bg-green-100 text-green-700">
      정상
    </span>
  );
}

/**
 * 날짜 포맷팅 (YYYY.MM.DD)
 */
function formatDate(dateStr) {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
}
