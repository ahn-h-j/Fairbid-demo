import { CATEGORIES } from './constants';

/**
 * 가격을 한국어 원화 형식으로 포맷
 * @param {number|null} price - 포맷할 가격
 * @returns {string} 포맷된 가격 문자열 (예: "1,000원")
 */
export function formatPrice(price) {
  if (price == null) return '-';
  return `${new Intl.NumberFormat('ko-KR').format(price)}원`;
}

/**
 * 카테고리 코드를 한글 라벨로 변환
 * @param {string} category - 카테고리 코드
 * @returns {string} 한글 카테고리명
 */
export function formatCategory(category) {
  return CATEGORIES[category] || category;
}

/**
 * 숫자를 2자리로 패딩
 * @param {number} n - 패딩할 숫자
 * @returns {string} 2자리 문자열
 */
export function padZero(n) {
  return n.toString().padStart(2, '0');
}

/**
 * ISO 날짜 문자열을 한국어 날짜로 포맷
 * @param {string} dateStr - ISO 8601 날짜 문자열
 * @returns {string} 포맷된 날짜
 */
export function formatDate(dateStr) {
  if (!dateStr) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateStr));
}

/**
 * 숫자를 천단위 구분자가 있는 문자열로 변환 (입력 필드용)
 * @param {string|number} value - 변환할 값
 * @returns {string} 천단위 구분자가 추가된 문자열
 */
export function formatNumberInput(value) {
  if (value === '' || value == null) return '';
  // 숫자만 추출
  const numericValue = String(value).replace(/[^0-9]/g, '');
  if (numericValue === '') return '';
  // 천단위 구분자 추가
  return new Intl.NumberFormat('ko-KR').format(Number(numericValue));
}

/**
 * 천단위 구분자가 있는 문자열에서 순수 숫자 추출
 * @param {string} value - 천단위 구분자가 있는 문자열
 * @returns {string} 숫자만 있는 문자열
 */
export function parseNumberInput(value) {
  if (value === '' || value == null) return '';
  return String(value).replace(/[^0-9]/g, '');
}

/**
 * 전화번호에 하이픈 자동 추가 (입력 필드용)
 * @param {string} value - 입력값
 * @returns {string} 하이픈이 추가된 전화번호
 */
export function formatPhoneInput(value) {
  if (!value) return '';
  // 숫자만 추출
  const numbers = value.replace(/[^0-9]/g, '');
  // 최대 11자리
  const limited = numbers.slice(0, 11);

  // 하이픈 추가
  if (limited.length <= 3) {
    return limited;
  } else if (limited.length <= 7) {
    return `${limited.slice(0, 3)}-${limited.slice(3)}`;
  } else {
    return `${limited.slice(0, 3)}-${limited.slice(3, 7)}-${limited.slice(7)}`;
  }
}

/**
 * 전화번호를 010-0000-0000 형식으로 포맷 (표시용)
 * @param {string} phone - 전화번호 (숫자만 또는 하이픈 포함)
 * @returns {string} 하이픈이 포함된 전화번호
 */
export function formatPhone(phone) {
  if (!phone) return '-';
  // 이미 하이픈이 있으면 그대로 반환
  if (phone.includes('-')) return phone;
  // 숫자만 추출
  const numbers = phone.replace(/[^0-9]/g, '');
  if (numbers.length === 11) {
    return `${numbers.slice(0, 3)}-${numbers.slice(3, 7)}-${numbers.slice(7)}`;
  } else if (numbers.length === 10) {
    return `${numbers.slice(0, 3)}-${numbers.slice(3, 6)}-${numbers.slice(6)}`;
  }
  return phone;
}
