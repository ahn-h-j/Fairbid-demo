/**
 * 알림 메시지 컴포넌트
 * 타입별 아이콘 + 배경색 + 링 스타일
 */
export default function Alert({ type, message, onClose }) {
  if (!message) return null;

  const styles = {
    success: {
      wrapper: 'bg-emerald-50 ring-emerald-200/60 text-emerald-800',
      icon: (
        <svg
          className="w-4 h-4 text-emerald-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
        </svg>
      ),
    },
    error: {
      wrapper: 'bg-red-50 ring-red-200/60 text-red-800',
      icon: (
        <svg
          className="w-4 h-4 text-red-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M6 18L18 6M6 6l12 12"
          />
        </svg>
      ),
    },
    info: {
      wrapper: 'bg-blue-50 ring-blue-200/60 text-blue-800',
      icon: (
        <svg
          className="w-4 h-4 text-blue-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      ),
    },
    warning: {
      wrapper: 'bg-amber-50 ring-amber-200/60 text-amber-800',
      icon: (
        <svg
          className="w-4 h-4 text-amber-500"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"
          />
        </svg>
      ),
    },
  };

  const config = styles[type] || styles.info;

  return (
    <div
      className={`flex items-center gap-3 px-4 py-3 rounded-xl ring-1 animate-slide-up ${config.wrapper}`}
      role="alert"
      aria-live="polite"
    >
      <div className="shrink-0">{config.icon}</div>
      <span className="text-[13px] font-medium flex-1">{message}</span>
      {onClose ? (
        <button
          type="button"
          onClick={onClose}
          className="shrink-0 opacity-50 hover:opacity-100 transition-opacity p-0.5"
          aria-label="알림 닫기"
        >
          <svg
            className="w-3.5 h-3.5"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2.5}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      ) : null}
    </div>
  );
}
