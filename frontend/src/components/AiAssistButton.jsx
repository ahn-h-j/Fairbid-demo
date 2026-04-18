import { useState } from 'react';
import { requestAiAuctionAssist } from '../api/mutations';
import Spinner from './Spinner';

/**
 * AI 경매 어시스턴트 호출 버튼.
 *
 * 활성화 조건:
 * - 이미지 1장 이상 업로드
 *
 * title/category 입력은 활성화 조건이 아니다 (폼의 다른 필드와 독립적).
 * category 가 선택돼 있으면 백엔드로 같이 보내고, 비어있으면 AI 가 이미지/메모로 추론한다.
 *
 * @param {object} props
 * @param {string} [props.category] - 카테고리 코드 (선택)
 * @param {string} [props.memo] - 구조화 힌트를 자연어로 조립한 문자열 (선택)
 * @param {string[]} props.imageUrls - 업로드된 이미지 URL 배열
 * @param {(result: {suggestedPrices: {low:number, mid:number, high:number}, generatedDescription: string}) => void} props.onResult
 * @param {boolean} [props.disabled] - 외부 사유로 비활성화 (예: 폼 제출 중)
 */
export default function AiAssistButton({ category, memo, imageUrls, onResult, disabled = false }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const hasImages = Array.isArray(imageUrls) && imageUrls.length > 0;
  const isDisabled = disabled || loading || !hasImages;

  const handleClick = async () => {
    if (isDisabled) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const payload = { imageUrls };
      if (category) {
        payload.category = category;
      }
      const trimmedMemo = (memo ?? '').trim();
      if (trimmedMemo) {
        payload.memo = trimmedMemo;
      }
      const result = await requestAiAuctionAssist(payload);
      onResult(result);
    } catch (err) {
      setError(err?.message || 'AI 추천을 가져오지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setLoading(false);
    }
  };

  // 비활성화 사유 안내
  let disabledReason = null;
  if (!loading && !disabled && !hasImages) {
    disabledReason = '이미지를 1장 이상 업로드하면 사용할 수 있어요.';
  }

  return (
    <div className="space-y-2">
      <button
        type="button"
        onClick={handleClick}
        disabled={isDisabled}
        aria-busy={loading}
        className="w-full flex items-center justify-center gap-2 py-3 bg-gradient-to-r from-purple-500 to-indigo-500 text-white text-[13px] font-semibold rounded-xl hover:from-purple-600 hover:to-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed btn-press shadow-sm shadow-indigo-500/20"
        style={{ transition: 'opacity 200ms, box-shadow 200ms, background-color 200ms' }}
      >
        {loading ? (
          <>
            <Spinner size="sm" className="border-white border-t-transparent" />
            AI가 분석 중…
          </>
        ) : (
          <>
            <svg
              className="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"
              />
            </svg>
            AI 추천 받기
          </>
        )}
      </button>

      {disabledReason ? (
        <p className="text-[11px] text-gray-400 text-center">{disabledReason}</p>
      ) : null}

      {error ? (
        <p className="text-[12px] text-red-500 text-center" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
