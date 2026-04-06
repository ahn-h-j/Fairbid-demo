/**
 * 페이지네이션 컴포넌트 (필 스타일)
 */
export default function Pagination({ currentPage, totalPages, onPageChange }) {
  if (totalPages <= 1) return null;

  const maxVisible = 5;
  let start = Math.max(0, currentPage - Math.floor(maxVisible / 2));
  const end = Math.min(totalPages, start + maxVisible);
  start = Math.max(0, end - maxVisible);

  const pages = [];
  for (let i = start; i < end; i++) {
    pages.push(i);
  }

  return (
    <nav className="flex items-center justify-center gap-1.5 pt-4" aria-label="페이지 네비게이션">
      {/* 이전 버튼 */}
      <button
        type="button"
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 0}
        className="w-9 h-9 flex items-center justify-center rounded-xl text-gray-500 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors duration-200 btn-press"
        aria-label="이전 페이지"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
        </svg>
      </button>

      {/* 페이지 번호 */}
      {pages.map((page) => (
        <button
          key={page}
          type="button"
          onClick={() => onPageChange(page)}
          className={`w-9 h-9 flex items-center justify-center rounded-xl text-[13px] font-semibold transition-colors duration-200 btn-press ${
            page === currentPage
              ? 'bg-gray-900 text-white shadow-sm'
              : 'text-gray-500 hover:bg-gray-100 hover:text-gray-700'
          }`}
          aria-label={`${page + 1}페이지`}
          aria-current={page === currentPage ? 'page' : undefined}
        >
          {page + 1}
        </button>
      ))}

      {/* 다음 버튼 */}
      <button
        type="button"
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage >= totalPages - 1}
        className="w-9 h-9 flex items-center justify-center rounded-xl text-gray-500 hover:bg-gray-100 hover:text-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors duration-200 btn-press"
        aria-label="다음 페이지"
      >
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
        </svg>
      </button>
    </nav>
  );
}
