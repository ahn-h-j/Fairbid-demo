import { useEffect, useState } from 'react';

/**
 * 앱 초기 로딩 시 표시되는 스플래시 화면 컴포넌트
 *
 * @param {object} props
 * @param {function} props.onComplete - 스플래시 애니메이션 완료 후 호출될 콜백
 * @param {number} [props.minDisplayTime=1500] - 최소 표시 시간 (ms)
 */
export default function SplashScreen({ onComplete, minDisplayTime = 1500 }) {
  const [fadeOut, setFadeOut] = useState(false);

  useEffect(() => {
    // 최소 표시 시간 후 페이드아웃 시작
    const fadeTimer = setTimeout(() => {
      setFadeOut(true);
    }, minDisplayTime);

    // 페이드아웃 애니메이션 후 완료 콜백 호출
    const completeTimer = setTimeout(() => {
      onComplete?.();
    }, minDisplayTime + 500); // 페이드아웃 애니메이션 시간 추가

    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(completeTimer);
    };
  }, [onComplete, minDisplayTime]);

  return (
    <div
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center
        bg-gradient-to-br from-blue-600 via-indigo-600 to-purple-700
        transition-opacity duration-500 ${fadeOut ? 'opacity-0' : 'opacity-100'}`}
    >
      {/* 배경 장식 요소 */}
      <div className="absolute inset-0 overflow-hidden">
        {/* 원형 그라데이션 */}
        <div className="absolute -top-1/4 -right-1/4 w-96 h-96 bg-white/10 rounded-full blur-3xl" />
        <div className="absolute -bottom-1/4 -left-1/4 w-96 h-96 bg-white/10 rounded-full blur-3xl" />
      </div>

      {/* 메인 컨텐츠 */}
      <div className="relative flex flex-col items-center gap-6 animate-fade-in">
        {/* 로고 아이콘 (상승 그래프 - favicon과 동일) */}
        <div className="animate-bounce-in">
          <div className="w-20 h-20 bg-white/20 backdrop-blur-sm rounded-2xl flex items-center justify-center shadow-2xl">
            <svg
              className="w-10 h-10 text-white"
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
        </div>

        {/* 브랜드명 */}
        <h1 className="text-4xl font-bold text-white tracking-tight drop-shadow-lg">FairBid</h1>

        {/* 슬로건 */}
        <p className="text-white/80 text-lg font-medium tracking-wide">호구 없는 경매</p>

        {/* 로딩 인디케이터 */}
        <div className="mt-8 flex gap-2">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="w-2.5 h-2.5 bg-white/80 rounded-full animate-bounce"
              style={{
                animationDelay: `${i * 0.15}s`,
                animationDuration: '0.8s',
              }}
            />
          ))}
        </div>
      </div>

      {/* 하단 버전 정보 */}
      <div className="absolute bottom-8 text-white/50 text-sm">v1.0.0</div>
    </div>
  );
}
