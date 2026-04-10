/**
 * 카테고리 상수 및 한글 라벨 매핑
 */
export const CATEGORIES = {
  ELECTRONICS: '전자기기',
  FASHION: '패션/의류',
  HOME: '가구/생활',
  SPORTS: '스포츠/레저',
  HOBBY: '취미/수집',
  OTHER: '기타',
};

/**
 * 경매 상태별 라벨 및 Tailwind 클래스
 */
export const STATUSES = {
  BIDDING: { label: '진행중', className: 'bg-green-100 text-green-800' },
  INSTANT_BUY_PENDING: { label: '즉시구매 대기', className: 'bg-blue-100 text-blue-800' },
  ENDED: { label: '종료', className: 'bg-gray-200 text-gray-700' },
  FAILED: { label: '유찰', className: 'bg-red-100 text-red-800' },
  CANCELLED: { label: '취소됨', className: 'bg-gray-100 text-gray-500' },
};

/**
 * 경매 기간 선택 옵션
 */
export const DURATIONS = [
  { value: 'HOURS_24', label: '24시간' },
  { value: 'HOURS_48', label: '48시간' },
];

/**
 * 입찰 타입
 */
export const BID_TYPES = {
  ONE_TOUCH: 'ONE_TOUCH',
  DIRECT: 'DIRECT',
  INSTANT_BUY: 'INSTANT_BUY',
};

/**
 * 타이머 임계값 (초 단위)
 */
export const TIMER_WARNING_THRESHOLD = 300; // 5분
export const TIMER_DANGER_THRESHOLD = 60; // 1분
