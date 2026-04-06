import { useState, useEffect } from 'react';

/**
 * 이미지 갤러리 컴포넌트
 * 메인 이미지 + 썸네일 네비게이션
 *
 * @param {object} props
 * @param {string[]} props.images - 이미지 URL 배열
 * @param {string} [props.alt='상품 이미지'] - 이미지 alt 텍스트
 */
export default function ImageGallery({ images = [], alt = '상품 이미지' }) {
  const [activeIndex, setActiveIndex] = useState(0);

  // images 변경 시 activeIndex 범위 보정
  useEffect(() => {
    if (!images || images.length === 0) {
      setActiveIndex(0);
      return;
    }
    setActiveIndex((prev) => Math.min(prev, images.length - 1));
  }, [images]);

  // 이미지가 없을 경우 플레이스홀더
  if (!images || images.length === 0) {
    return (
      <div className="bg-gray-100 rounded-2xl aspect-[4/3] flex items-center justify-center">
        <div className="text-center">
          <svg
            className="w-16 h-16 text-gray-300 mx-auto mb-2"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={1}
              d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
            />
          </svg>
          <p className="text-[13px] text-gray-400">등록된 이미지가 없습니다</p>
        </div>
      </div>
    );
  }

  const handlePrev = () => {
    setActiveIndex((prev) => (prev === 0 ? images.length - 1 : prev - 1));
  };

  const handleNext = () => {
    setActiveIndex((prev) => (prev === images.length - 1 ? 0 : prev + 1));
  };

  return (
    <div className="space-y-3">
      {/* 메인 이미지 */}
      <div className="relative bg-gray-100 rounded-2xl overflow-hidden aspect-[4/3] group">
        <img
          src={images[activeIndex]}
          alt={`${alt} ${activeIndex + 1}`}
          className="w-full h-full object-contain"
          loading={activeIndex === 0 ? 'eager' : 'lazy'}
        />

        {/* 이미지 개수 표시 */}
        {images.length > 1 ? (
          <div className="absolute bottom-3 right-3 px-2.5 py-1 bg-black/60 text-white text-[11px] font-medium rounded-full">
            {activeIndex + 1} / {images.length}
          </div>
        ) : null}

        {/* 이전/다음 버튼 (이미지 2장 이상일 때만) */}
        {images.length > 1 ? (
          <>
            <button
              type="button"
              onClick={handlePrev}
              className="absolute left-2 top-1/2 -translate-y-1/2 w-9 h-9 bg-white/90 hover:bg-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow-lg"
              aria-label="이전 이미지"
            >
              <svg
                className="w-5 h-5 text-gray-700"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M15 19l-7-7 7-7"
                />
              </svg>
            </button>
            <button
              type="button"
              onClick={handleNext}
              className="absolute right-2 top-1/2 -translate-y-1/2 w-9 h-9 bg-white/90 hover:bg-white rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity shadow-lg"
              aria-label="다음 이미지"
            >
              <svg
                className="w-5 h-5 text-gray-700"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5l7 7-7 7"
                />
              </svg>
            </button>
          </>
        ) : null}
      </div>

      {/* 썸네일 (이미지 2장 이상일 때만) */}
      {images.length > 1 ? (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {images.map((url, index) => (
            <button
              key={url}
              type="button"
              onClick={() => setActiveIndex(index)}
              className={`shrink-0 w-16 h-16 rounded-lg overflow-hidden transition-[border-color,opacity] duration-200 ${
                activeIndex === index
                  ? 'ring-2 ring-blue-500 ring-offset-1'
                  : 'ring-1 ring-gray-200 hover:ring-gray-300 opacity-70 hover:opacity-100'
              }`}
              aria-label={`이미지 ${index + 1} 보기`}
              aria-pressed={activeIndex === index}
            >
              <img
                src={url}
                alt={`${alt} 썸네일 ${index + 1}`}
                className="w-full h-full object-cover"
                loading="lazy"
              />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
