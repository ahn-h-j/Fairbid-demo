import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useNotifications, useUnreadCount, markAsRead } from '../api/useNotifications';
import { getServerTime } from '../api/client';

/**
 * 알림 드롭다운 컴포넌트
 * 헤더에 표시되며, 클릭 시 알림 목록을 보여준다.
 */
export default function NotificationDropdown() {
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);
  const navigate = useNavigate();

  const { notifications, mutate: mutateNotifications } = useNotifications();
  const { unreadCount, mutate: mutateCount } = useUnreadCount();

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // 알림 클릭 핸들러
  const handleNotificationClick = async (notification) => {
    // 읽음 처리
    if (!notification.read) {
      try {
        await markAsRead(notification.id);
        mutateNotifications();
        mutateCount();
      } catch (error) {
        console.error('알림 읽음 처리 실패:', error);
      }
    }

    // 알림 타입에 따라 적절한 페이지로 이동
    const tradeRelatedTypes = [
      'METHOD_SELECTED',
      'ARRANGEMENT_PROPOSED',
      'ARRANGEMENT_COUNTER_PROPOSED',
      'ARRANGEMENT_ACCEPTED',
      'DELIVERY_ADDRESS_SUBMITTED',
      'DELIVERY_SHIPPED',
      'TRADE_COMPLETED',
      'PAYMENT_CONFIRMED',
      'PAYMENT_VERIFIED',
      'PAYMENT_REJECTED',
    ];

    if (tradeRelatedTypes.includes(notification.type) && notification.tradeId) {
      // 거래 관련 알림 → 거래 상세 페이지로 이동
      navigate(`/trades/${notification.tradeId}`);
      setIsOpen(false);
    } else if (notification.type === 'WINNING' || notification.type === 'TRANSFER') {
      // 낙찰/승계 알림 → 내 거래 목록으로 이동
      navigate('/trades');
      setIsOpen(false);
    } else if (notification.auctionId) {
      // 그 외 알림 → 경매 상세로 이동
      navigate(`/auctions/${notification.auctionId}`);
      setIsOpen(false);
    }
  };

  // 알림 유형별 아이콘
  const getNotificationIcon = (type) => {
    switch (type) {
      case 'WINNING':
        return '🎉';
      case 'TRANSFER':
        return '🔄';
      case 'SECOND_RANK_STANDBY':
        return '⏳';
      case 'FAILED':
        return '😢';
      case 'RESPONSE_REMINDER':
      case 'PAYMENT_REMINDER':
        return '⏰';
      case 'PAYMENT_COMPLETED':
      case 'TRADE_COMPLETED':
        return '✅';
      case 'NO_SHOW_PENALTY':
        return '⚠️';
      case 'METHOD_SELECTED':
      case 'ARRANGEMENT_PROPOSED':
      case 'ARRANGEMENT_COUNTER_PROPOSED':
      case 'ARRANGEMENT_ACCEPTED':
        return '📅';
      case 'DELIVERY_ADDRESS_SUBMITTED':
        return '📮';
      case 'DELIVERY_SHIPPED':
        return '📦';
      default:
        return '🔔';
    }
  };

  // 상대 시간 표시
  const getRelativeTime = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    if (isNaN(date.getTime())) return '';

    const now = getServerTime();
    const diffMs = now - date;
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);

    if (diffMins < 1) return '방금 전';
    if (diffMins < 60) return `${diffMins}분 전`;
    if (diffHours < 24) return `${diffHours}시간 전`;
    return date.toLocaleDateString('ko-KR');
  };

  return (
    <div className="relative" ref={dropdownRef}>
      {/* 알림 버튼 */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="relative p-2 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-xl transition-colors"
        aria-label="알림"
      >
        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>
        {/* 읽지 않은 알림 배지 */}
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-white bg-red-500 rounded-full">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {/* 드롭다운 메뉴 */}
      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 bg-white rounded-2xl shadow-xl ring-1 ring-black/5 overflow-hidden z-50">
          <div className="px-4 py-3 border-b border-gray-100">
            <h3 className="text-sm font-bold text-gray-900">알림</h3>
          </div>

          <div className="max-h-80 overflow-y-auto">
            {notifications.length === 0 ? (
              <div className="px-4 py-8 text-center text-gray-400 text-sm">알림이 없습니다</div>
            ) : (
              <ul className="divide-y divide-gray-50">
                {notifications.map((notification) => (
                  <li key={notification.id}>
                    <button
                      type="button"
                      onClick={() => handleNotificationClick(notification)}
                      className={`w-full px-4 py-3 text-left hover:bg-gray-50 transition-colors ${
                        !notification.read ? 'bg-blue-50/50' : ''
                      }`}
                    >
                      <div className="flex gap-3">
                        <span className="text-lg flex-shrink-0">
                          {getNotificationIcon(notification.type)}
                        </span>
                        <div className="flex-1 min-w-0">
                          <p
                            className={`text-[13px] font-semibold truncate ${
                              !notification.read ? 'text-gray-900' : 'text-gray-600'
                            }`}
                          >
                            {notification.title}
                          </p>
                          <p className="text-[12px] text-gray-500 line-clamp-2 mt-0.5">
                            {notification.body}
                          </p>
                          <p className="text-[11px] text-gray-400 mt-1">
                            {getRelativeTime(notification.createdAt)}
                          </p>
                        </div>
                        {!notification.read && (
                          <span className="w-2 h-2 bg-blue-500 rounded-full flex-shrink-0 mt-1.5" />
                        )}
                      </div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
