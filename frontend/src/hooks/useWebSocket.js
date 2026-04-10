import { useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';

/**
 * 실시간 경매 업데이트를 위한 WebSocket 훅
 * STOMP over SockJS로 /topic/auctions/{auctionId}를 구독하여
 * 입찰 업데이트와 경매 종료 이벤트를 수신한다.
 *
 * @param {string|number|null} auctionId - 구독할 경매 ID (null이면 연결하지 않음)
 * @param {object} callbacks - 이벤트 콜백
 * @param {function} [callbacks.onBidUpdate] - 입찰 업데이트 수신 시 호출
 * @param {function} [callbacks.onAuctionClosed] - 경매 종료 수신 시 호출
 */
export function useWebSocket(auctionId, { onBidUpdate, onAuctionClosed } = {}) {
  const clientRef = useRef(null);
  const subscriptionRef = useRef(null);

  // 콜백 ref로 관리하여 재구독 방지
  const onBidUpdateRef = useRef(onBidUpdate);
  const onAuctionClosedRef = useRef(onAuctionClosed);
  useEffect(() => {
    onBidUpdateRef.current = onBidUpdate;
  }, [onBidUpdate]);
  useEffect(() => {
    onAuctionClosedRef.current = onAuctionClosed;
  }, [onAuctionClosed]);

  const connect = useCallback(() => {
    if (!auctionId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000, // 5초 후 자동 재연결
      debug: () => {}, // 프로덕션에서 디버그 로그 비활성화

      onConnect: () => {
        // 경매 토픽 구독
        subscriptionRef.current = client.subscribe(`/topic/auctions/${auctionId}`, (message) => {
          try {
            const data = JSON.parse(message.body);

            if (data.type === 'AUCTION_CLOSED') {
              onAuctionClosedRef.current?.(data);
            } else if (data.currentPrice !== undefined) {
              // currentPrice 필드가 있으면 입찰 업데이트
              onBidUpdateRef.current?.(data);
            }
          } catch (err) {
            console.error('[WebSocket] 메시지 파싱 오류:', err);
          }
        });
      },

      onStompError: (frame) => {
        console.error('[WebSocket] STOMP 오류:', frame.headers?.message);
      },
    });

    client.activate();
    clientRef.current = client;
  }, [auctionId]);

  useEffect(() => {
    connect();

    return () => {
      // 구독 해제
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
        subscriptionRef.current = null;
      }
      // 연결 종료
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
    };
  }, [connect]);
}
