import { useState } from 'react';
import { Link } from 'react-router-dom';
import { createAuction } from '../api/mutations';
import Alert from '../components/Alert';
import ImageUpload from '../components/ImageUpload';
import Spinner from '../components/Spinner';
import { CATEGORIES, DURATIONS } from '../utils/constants';
import { formatPrice, formatNumberInput, parseNumberInput } from '../utils/formatters';

/**
 * 경매 등록 페이지
 * 섹션별로 구분된 폼, 유효성 검사 후 경매를 생성한다.
 */
export default function AuctionCreatePage() {
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    category: '',
    startPrice: '',
    instantBuyPrice: '',
    duration: 'HOURS_24',
    // 거래 방식 설정
    directTradeAvailable: true,
    deliveryAvailable: true,
    directTradeLocation: '',
    // 이미지
    imageUrls: [],
  });
  const [submitting, setSubmitting] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState(null);
  const [createdAuction, setCreatedAuction] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    setError(null);
  };

  // 금액 입력 핸들러 (천단위 구분자 처리)
  const handlePriceChange = (e) => {
    const { name, value } = e.target;
    // 숫자만 추출하여 저장
    const numericValue = parseNumberInput(value);
    setFormData((prev) => ({ ...prev, [name]: numericValue }));
    setError(null);
  };

  const validate = () => {
    if (!formData.title.trim()) return '제목을 입력해주세요.';
    if (!formData.category) return '카테고리를 선택해주세요.';
    const startPrice = parseInt(formData.startPrice, 10);
    if (!startPrice || startPrice < 1) return '시작 가격을 1원 이상으로 입력해주세요.';
    if (formData.instantBuyPrice) {
      const instantBuyPrice = parseInt(formData.instantBuyPrice, 10);
      if (instantBuyPrice <= startPrice) return '즉시 구매가는 시작 가격보다 높아야 합니다.';
    }
    // 거래 방식 검증
    if (!formData.directTradeAvailable && !formData.deliveryAvailable) {
      return '최소 1개의 거래 방식을 선택해주세요.';
    }
    if (formData.directTradeAvailable && !formData.directTradeLocation.trim()) {
      return '직거래 선택 시 희망 위치를 입력해주세요.';
    }
    return null;
  };

  const handleCheckboxChange = (e) => {
    const { name, checked } = e.target;
    setFormData((prev) => ({ ...prev, [name]: checked }));
    setError(null);
  };

  /**
   * 이미지 URL 배열 변경 핸들러
   * @param {string[]} imageUrls
   */
  const handleImagesChange = (imageUrls) => {
    setFormData((prev) => ({ ...prev, imageUrls }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    setSubmitting(true);
    setError(null);

    try {
      const payload = {
        title: formData.title.trim(),
        description: formData.description.trim() || null,
        category: formData.category,
        startPrice: parseInt(formData.startPrice, 10),
        instantBuyPrice: formData.instantBuyPrice ? parseInt(formData.instantBuyPrice, 10) : null,
        duration: formData.duration,
        // 거래 방식 설정
        directTradeAvailable: formData.directTradeAvailable,
        deliveryAvailable: formData.deliveryAvailable,
        directTradeLocation: formData.directTradeAvailable
          ? formData.directTradeLocation.trim()
          : null,
        // 이미지
        imageUrls: formData.imageUrls.length > 0 ? formData.imageUrls : null,
      };
      const result = await createAuction(payload);
      setCreatedAuction(result);
    } catch (err) {
      setError(err.message || '경매 등록에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  // 성공 상태
  if (createdAuction) {
    return (
      <div className="max-w-md mx-auto animate-scale-pop">
        <div className="bg-white rounded-2xl p-8 ring-1 ring-black/[0.04] shadow-lg shadow-green-500/5 text-center">
          <div className="w-16 h-16 mx-auto mb-5 bg-gradient-to-br from-green-400 to-emerald-500 rounded-2xl flex items-center justify-center shadow-lg shadow-green-500/25">
            <svg
              className="w-8 h-8 text-white"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2.5}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
          <h2 className="text-xl font-bold text-gray-900 mb-2">경매가 등록되었습니다!</h2>
          <p className="text-sm text-gray-500 mb-6">
            시작 가격 {formatPrice(createdAuction.startPrice)}으로 경매가 시작됩니다.
          </p>
          <div className="flex flex-col gap-2.5">
            <Link
              to={`/auctions/${createdAuction.id}`}
              className="w-full py-3 bg-gray-900 text-white text-[13px] font-semibold rounded-xl hover:bg-gray-800 transition-colors btn-press text-center shadow-sm"
            >
              경매 상세 보기
            </Link>
            <Link
              to="/auctions"
              className="w-full py-3 bg-gray-50 text-gray-600 text-[13px] font-semibold rounded-xl hover:bg-gray-100 transition-colors btn-press text-center"
            >
              목록으로 돌아가기
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-xl mx-auto animate-fade-in">
      {/* 페이지 헤더 */}
      <div className="mb-6">
        <h1 className="text-[22px] font-bold text-gray-900 tracking-tight">경매 등록</h1>
        <p className="text-[13px] text-gray-400 mt-0.5">
          새로운 경매를 등록하고 적정가를 찾아보세요
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-5">
        {/* 에러 메시지 */}
        {error ? <Alert type="error" message={error} onClose={() => setError(null)} /> : null}

        {/* 상품 이미지 섹션 */}
        <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04] space-y-4">
          <h2 className="text-[13px] font-bold text-gray-500 uppercase tracking-wider">
            상품 이미지
          </h2>
          <ImageUpload
            images={formData.imageUrls}
            onChange={handleImagesChange}
            maxImages={5}
            onUploadingChange={setIsUploading}
          />
        </div>

        {/* 기본 정보 섹션 */}
        <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04] space-y-4">
          <h2 className="text-[13px] font-bold text-gray-500 uppercase tracking-wider">
            기본 정보
          </h2>

          {/* 제목 */}
          <div>
            <label htmlFor="title" className="block text-[13px] font-semibold text-gray-700 mb-1.5">
              제목 <span className="text-red-400 font-normal">*</span>
            </label>
            <input
              id="title"
              name="title"
              type="text"
              value={formData.title}
              onChange={handleChange}
              placeholder="경매 상품 제목을 입력하세요"
              className="w-full px-4 py-3 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 input-glow transition-colors duration-200"
              required
              autoComplete="off"
            />
          </div>

          {/* 설명 */}
          <div>
            <label
              htmlFor="description"
              className="block text-[13px] font-semibold text-gray-700 mb-1.5"
            >
              설명
            </label>
            <textarea
              id="description"
              name="description"
              value={formData.description}
              onChange={handleChange}
              placeholder="상품에 대한 상세 설명을 입력하세요"
              rows={4}
              className="w-full px-4 py-3 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 placeholder-gray-400 resize-none focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 input-glow transition-colors duration-200"
            />
          </div>

          {/* 카테고리 */}
          <div>
            <label
              htmlFor="category"
              className="block text-[13px] font-semibold text-gray-700 mb-1.5"
            >
              카테고리 <span className="text-red-400 font-normal">*</span>
            </label>
            <select
              id="category"
              name="category"
              value={formData.category}
              onChange={handleChange}
              className="w-full px-4 py-3 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-colors duration-200 cursor-pointer appearance-none"
              required
              style={{
                backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke='%239ca3af'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M19 9l-7 7-7-7'/%3E%3C/svg%3E")`,
                backgroundPosition: 'right 12px center',
                backgroundSize: '16px',
                backgroundRepeat: 'no-repeat',
              }}
            >
              <option value="" disabled>
                카테고리를 선택하세요
              </option>
              {Object.entries(CATEGORIES).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* 가격 설정 섹션 */}
        <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04] space-y-4">
          <h2 className="text-[13px] font-bold text-gray-500 uppercase tracking-wider">
            가격 설정
          </h2>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* 시작 가격 */}
            <div>
              <label
                htmlFor="startPrice"
                className="block text-[13px] font-semibold text-gray-700 mb-1.5"
              >
                시작 가격 <span className="text-red-400 font-normal">*</span>
              </label>
              <div className="relative">
                <input
                  id="startPrice"
                  name="startPrice"
                  type="text"
                  value={formatNumberInput(formData.startPrice)}
                  onChange={handlePriceChange}
                  placeholder="0"
                  className="w-full pl-4 pr-10 py-3 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 input-glow transition-colors duration-200"
                  required
                  inputMode="numeric"
                />
                <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-gray-400 font-medium">
                  원
                </span>
              </div>
            </div>

            {/* 즉시 구매가 */}
            <div>
              <label
                htmlFor="instantBuyPrice"
                className="block text-[13px] font-semibold text-gray-700 mb-1.5"
              >
                즉시 구매가 <span className="text-gray-400 font-normal text-[11px]">(선택)</span>
              </label>
              <div className="relative">
                <input
                  id="instantBuyPrice"
                  name="instantBuyPrice"
                  type="text"
                  value={formatNumberInput(formData.instantBuyPrice)}
                  onChange={handlePriceChange}
                  placeholder="0"
                  className="w-full pl-4 pr-10 py-3 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 input-glow transition-colors duration-200"
                  inputMode="numeric"
                />
                <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-gray-400 font-medium">
                  원
                </span>
              </div>
              <p className="text-[11px] text-gray-400 mt-1.5 ml-1">시작 가격보다 높게 설정하세요</p>
            </div>
          </div>
        </div>

        {/* 경매 기간 섹션 */}
        <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04] space-y-4">
          <h2 className="text-[13px] font-bold text-gray-500 uppercase tracking-wider">
            경매 기간
          </h2>

          <div className="grid grid-cols-2 gap-3">
            {DURATIONS.map(({ value, label }) => (
              <label
                key={value}
                className={`relative flex items-center justify-center py-3.5 px-4 rounded-xl cursor-pointer transition-colors duration-200 btn-press ${
                  formData.duration === value
                    ? 'bg-gray-900 text-white shadow-sm'
                    : 'bg-gray-50 text-gray-600 hover:bg-gray-100'
                }`}
              >
                <input
                  type="radio"
                  name="duration"
                  value={value}
                  checked={formData.duration === value}
                  onChange={handleChange}
                  className="sr-only"
                />
                <span className="text-[13px] font-semibold">{label}</span>
              </label>
            ))}
          </div>
        </div>

        {/* 거래 방식 섹션 */}
        <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04] space-y-4">
          <h2 className="text-[13px] font-bold text-gray-500 uppercase tracking-wider">
            거래 방식
          </h2>
          <p className="text-[12px] text-gray-400 -mt-2">
            최소 1개 이상의 거래 방식을 선택해주세요
          </p>

          <div className="space-y-3">
            {/* 직거래 옵션 */}
            <label
              className={`flex items-start gap-3 p-4 rounded-xl cursor-pointer transition-colors duration-200 ${
                formData.directTradeAvailable
                  ? 'bg-blue-50 ring-1 ring-blue-200'
                  : 'bg-gray-50 hover:bg-gray-100'
              }`}
            >
              <input
                type="checkbox"
                name="directTradeAvailable"
                checked={formData.directTradeAvailable}
                onChange={handleCheckboxChange}
                className="w-5 h-5 text-blue-600 rounded border-gray-300 focus:ring-blue-500 mt-0.5"
              />
              <div className="flex-1">
                <span className="text-[14px] font-semibold text-gray-900">직거래</span>
                <p className="text-[12px] text-gray-500 mt-0.5">구매자와 직접 만나서 거래</p>
              </div>
            </label>

            {/* 직거래 위치 입력 (직거래 선택 시만 표시) */}
            {formData.directTradeAvailable && (
              <div className="ml-8 animate-fade-in">
                <label
                  htmlFor="directTradeLocation"
                  className="block text-[13px] font-semibold text-gray-700 mb-1.5"
                >
                  희망 거래 위치 <span className="text-red-400 font-normal">*</span>
                </label>
                <input
                  id="directTradeLocation"
                  name="directTradeLocation"
                  type="text"
                  value={formData.directTradeLocation}
                  onChange={handleChange}
                  placeholder="예: 강남역 2번 출구, 홍대입구역 등"
                  className="w-full px-4 py-3 bg-white border border-gray-200 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-300 transition-colors duration-200"
                />
                <p className="text-[11px] text-gray-400 mt-1.5 ml-1">
                  구매자에게 표시될 거래 희망 장소입니다
                </p>
              </div>
            )}

            {/* 택배 옵션 */}
            <label
              className={`flex items-start gap-3 p-4 rounded-xl cursor-pointer transition-colors duration-200 ${
                formData.deliveryAvailable
                  ? 'bg-blue-50 ring-1 ring-blue-200'
                  : 'bg-gray-50 hover:bg-gray-100'
              }`}
            >
              <input
                type="checkbox"
                name="deliveryAvailable"
                checked={formData.deliveryAvailable}
                onChange={handleCheckboxChange}
                className="w-5 h-5 text-blue-600 rounded border-gray-300 focus:ring-blue-500 mt-0.5"
              />
              <div className="flex-1">
                <span className="text-[14px] font-semibold text-gray-900">택배</span>
                <p className="text-[12px] text-gray-500 mt-0.5">택배를 통해 배송</p>
              </div>
            </label>
          </div>
        </div>

        {/* 제출 버튼 */}
        <button
          type="submit"
          disabled={submitting || isUploading}
          className="w-full flex items-center justify-center gap-2 py-3.5 bg-gradient-to-r from-blue-500 to-blue-600 text-white text-[14px] font-semibold rounded-xl hover:from-blue-600 hover:to-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors duration-200 btn-press shadow-lg shadow-blue-500/20 hover:shadow-blue-500/30"
        >
          {submitting ? (
            <>
              <Spinner size="sm" className="border-white border-t-transparent" />
              등록 중…
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
                  d="M12 4v16m8-8H4"
                />
              </svg>
              경매 등록하기
            </>
          )}
        </button>
      </form>
    </div>
  );
}
