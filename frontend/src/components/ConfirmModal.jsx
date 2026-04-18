import { useEffect, useRef } from 'react';

/**
 * 커스텀 확인 모달.
 * native window.confirm 의 "localhost:xxxx 내용:" 라벨을 피하기 위한 대체 구현.
 *
 * @param {object} props
 * @param {boolean} props.open - 모달 표시 여부
 * @param {string} props.message - 표시할 메시지
 * @param {string} [props.confirmLabel] - 확인 버튼 라벨
 * @param {string} [props.cancelLabel] - 취소 버튼 라벨
 * @param {() => void} props.onConfirm
 * @param {() => void} props.onCancel
 */
export default function ConfirmModal({
  open,
  message,
  confirmLabel = '확인',
  cancelLabel = '취소',
  onConfirm,
  onCancel,
}) {
  const confirmButtonRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    // 포커스를 확인 버튼에 둬서 Enter 로 즉시 confirm 가능
    confirmButtonRef.current?.focus();

    // ESC 로 취소
    const handleKey = (e) => {
      if (e.key === 'Escape') {
        onCancel();
      }
    };
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [open, onCancel]);

  if (!open) return null;

  return (
    // 백드롭은 클릭으로만 닫히고 키보드 ESC는 window.keydown 으로 처리 (useEffect 참고)
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-modal-message"
      onClick={onCancel}
    >
      {/* 이벤트 버블 차단용 컨테이너 — 상호작용은 내부 버튼이 담당 */}
      {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions */}
      <div
        className="bg-white rounded-2xl shadow-xl max-w-sm w-[90%] p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <p
          id="confirm-modal-message"
          className="text-[14px] text-gray-800 leading-relaxed whitespace-pre-line"
        >
          {message}
        </p>
        <div className="mt-5 flex gap-2 justify-end">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 text-[13px] font-medium text-gray-600 hover:bg-gray-100 rounded-lg"
            style={{ transition: 'background-color 150ms' }}
          >
            {cancelLabel}
          </button>
          <button
            ref={confirmButtonRef}
            type="button"
            onClick={onConfirm}
            className="px-4 py-2 text-[13px] font-semibold text-white bg-indigo-500 hover:bg-indigo-600 rounded-lg"
            style={{ transition: 'background-color 150ms' }}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
