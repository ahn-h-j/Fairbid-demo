import { useTimer } from '../hooks/useTimer';

/**
 * 경매 카운트다운 타이머 컴포넌트
 * 남은 시간에 따라 시각적 상태가 변경된다.
 * - normal: 기본 (5분 초과)
 * - warning: 주의 (5분 이하, 주황색)
 * - danger: 위험 (1분 이하, 빨간색 + 펄스)
 */
export default function Timer({ endTime, compact = false }) {
  const { hours, minutes, seconds, timerState, isExpired } = useTimer(endTime);

  if (isExpired) {
    if (compact) return null;
    return (
      <div className="flex flex-col items-center gap-2">
        <div className="flex gap-2">
          <TimeBox value="00" label="시" muted />
          <span className="text-2xl text-gray-300 font-light self-start mt-1">:</span>
          <TimeBox value="00" label="분" muted />
          <span className="text-2xl text-gray-300 font-light self-start mt-1">:</span>
          <TimeBox value="00" label="초" muted />
        </div>
        <span className="text-xs text-gray-400 font-medium">경매 종료</span>
      </div>
    );
  }

  // compact 모드: 카드 위 흰색 텍스트
  if (compact) {
    const compactColors = {
      normal: 'text-white/90',
      warning: 'text-orange-300',
      danger: 'text-red-300 animate-pulse-subtle',
    };

    const h = String(hours).padStart(2, '0');
    const m = String(minutes).padStart(2, '0');
    const s = String(seconds).padStart(2, '0');

    return (
      <div className="flex items-center gap-1.5">
        <svg
          className="w-3 h-3 text-white/60"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
        <span
          className={`text-xs font-mono font-bold tabular-nums tracking-wide ${compactColors[timerState]}`}
          aria-label={`남은 시간 ${h}:${m}:${s}`}
        >
          {h}:{m}:{s}
        </span>
      </div>
    );
  }

  // 상세 페이지: 박스형 타이머
  const stateColors = {
    normal: { box: 'bg-gray-50 ring-gray-200/60', text: 'text-gray-900' },
    warning: { box: 'bg-orange-50 ring-orange-200/60', text: 'text-orange-600' },
    danger: { box: 'bg-red-50 ring-red-200/60', text: 'text-red-600' },
  };
  const colors = stateColors[timerState];

  return (
    <div className="flex flex-col items-center gap-2.5">
      <div
        className={`flex items-center gap-1.5 sm:gap-2 ${timerState === 'danger' ? 'animate-pulse-subtle' : ''}`}
      >
        <TimeBox
          value={String(hours).padStart(2, '0')}
          label="시"
          boxClass={colors.box}
          textClass={colors.text}
        />
        <span
          className={`text-xl sm:text-2xl font-light ${colors.text} opacity-40 self-start mt-2 sm:mt-3`}
        >
          :
        </span>
        <TimeBox
          value={String(minutes).padStart(2, '0')}
          label="분"
          boxClass={colors.box}
          textClass={colors.text}
        />
        <span
          className={`text-xl sm:text-2xl font-light ${colors.text} opacity-40 self-start mt-2 sm:mt-3`}
        >
          :
        </span>
        <TimeBox
          value={String(seconds).padStart(2, '0')}
          label="초"
          boxClass={colors.box}
          textClass={colors.text}
        />
      </div>
      {timerState === 'warning' && (
        <span className="text-[11px] text-orange-500 font-semibold bg-orange-50 px-2.5 py-0.5 rounded-full">
          곧 종료됩니다
        </span>
      )}
      {timerState === 'danger' && (
        <span className="text-[11px] text-red-600 font-bold bg-red-50 px-2.5 py-0.5 rounded-full">
          마감 임박!
        </span>
      )}
    </div>
  );
}

/** 개별 시간 박스 */
function TimeBox({
  value,
  label,
  boxClass = 'bg-gray-50 ring-gray-200/60',
  textClass = 'text-gray-900',
  muted = false,
}) {
  return (
    <div className="flex flex-col items-center gap-0.5">
      <div
        className={`w-12 h-14 sm:w-16 sm:h-[72px] flex items-center justify-center rounded-xl ring-1 ${muted ? 'bg-gray-50 ring-gray-100' : boxClass}`}
      >
        <span
          className={`text-2xl sm:text-[32px] font-mono font-bold tabular-nums ${muted ? 'text-gray-300' : textClass}`}
        >
          {value}
        </span>
      </div>
      <span className="text-[10px] text-gray-400 font-medium">{label}</span>
    </div>
  );
}
