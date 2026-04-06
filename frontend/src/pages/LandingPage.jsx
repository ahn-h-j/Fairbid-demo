import { Link } from 'react-router-dom';

/**
 * 랜딩 페이지
 * 서비스 소개
 */
export default function LandingPage() {

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 to-white">
      {/* 히어로 섹션 */}
      <section className="relative overflow-hidden">
        {/* 배경 장식 */}
        <div className="absolute inset-0 -z-10">
          <div className="absolute top-0 right-0 w-[600px] h-[600px] bg-gradient-to-br from-blue-100/60 to-violet-100/60 rounded-full blur-3xl translate-x-1/2 -translate-y-1/2" />
          <div className="absolute bottom-0 left-0 w-[400px] h-[400px] bg-gradient-to-br from-cyan-100/40 to-blue-100/40 rounded-full blur-3xl -translate-x-1/2 translate-y-1/2" />
        </div>

        <div className="max-w-5xl mx-auto px-6 pt-20 pb-24 sm:pt-28 sm:pb-32">
          <div className="text-center">
            {/* 뱃지 */}
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-blue-50 rounded-full mb-6">
              <span className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
              <span className="text-sm font-medium text-blue-700">실시간 경쟁 입찰</span>
            </div>

            {/* 메인 타이틀 */}
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold text-gray-900 tracking-tight mb-6">
              <span className="bg-gradient-to-r from-blue-600 to-violet-600 bg-clip-text text-transparent">
                호구 없는 경매
              </span>
              <br />
              <span className="text-gray-900">적정가를 찾아드립니다</span>
            </h1>

            {/* 설명 */}
            <p className="text-lg sm:text-xl text-gray-500 max-w-2xl mx-auto mb-10 leading-relaxed">
              깎이는 중고 거래는 그만!<br className="sm:hidden" />
              실시간 경쟁 입찰로 <span className="text-gray-700 font-medium">적정가 이상</span>을 받아보세요.
            </p>

            {/* CTA 버튼 */}
            <Link
              to="/auctions"
              className="inline-block px-8 py-4 bg-gradient-to-r from-blue-500 to-violet-600 text-white text-lg font-semibold rounded-2xl shadow-lg shadow-blue-500/25 hover:shadow-blue-500/40 transition-[transform,box-shadow] duration-300 hover:-translate-y-0.5"
            >
              경매 둘러보기
            </Link>
          </div>
        </div>
      </section>

      {/* 기능 소개 섹션 */}
      <section className="py-20 sm:py-28 bg-white">
        <div className="max-w-5xl mx-auto px-6">
          <div className="text-center mb-16">
            <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
              왜 FairBid인가요?
            </h2>
            <p className="text-gray-500 text-lg">
              판매자와 구매자 모두를 위한 공정한 거래
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
            {/* 기능 1 */}
            <div className="bg-gradient-to-br from-blue-50 to-white p-6 rounded-2xl ring-1 ring-blue-100">
              <div className="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center mb-4">
                <svg className="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
                </svg>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">실시간 경쟁 입찰</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                여러 구매자가 실시간으로 경쟁하여 적정가를 형성합니다. 깎이는 거래는 이제 그만!
              </p>
            </div>

            {/* 기능 2 */}
            <div className="bg-gradient-to-br from-violet-50 to-white p-6 rounded-2xl ring-1 ring-violet-100">
              <div className="w-12 h-12 bg-violet-100 rounded-xl flex items-center justify-center mb-4">
                <svg className="w-6 h-6 text-violet-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">자동 경매 연장</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                종료 5분 전 입찰 시 자동 연장! 마지막까지 공정한 경쟁 기회를 보장합니다.
              </p>
            </div>

            {/* 기능 3 */}
            <div className="bg-gradient-to-br from-green-50 to-white p-6 rounded-2xl ring-1 ring-green-100">
              <div className="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center mb-4">
                <svg className="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                </svg>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">2순위 낙찰 시스템</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                1순위 노쇼 시 2순위에게 자동 기회 부여. 거래 성사율을 높여드립니다.
              </p>
            </div>

            {/* 기능 4 */}
            <div className="bg-gradient-to-br from-amber-50 to-white p-6 rounded-2xl ring-1 ring-amber-100">
              <div className="w-12 h-12 bg-amber-100 rounded-xl flex items-center justify-center mb-4">
                <svg className="w-6 h-6 text-amber-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                </svg>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">실시간 알림</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                입찰, 낙찰, 거래 상태 변경 시 즉시 알림. 중요한 순간을 놓치지 마세요.
              </p>
            </div>

            {/* 기능 5 */}
            <div className="bg-gradient-to-br from-rose-50 to-white p-6 rounded-2xl ring-1 ring-rose-100">
              <div className="w-12 h-12 bg-rose-100 rounded-xl flex items-center justify-center mb-4">
                <svg className="w-6 h-6 text-rose-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">직거래 & 택배</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                원하는 거래 방식을 선택하세요. 직거래 위치 지정도 가능합니다.
              </p>
            </div>

            {/* 기능 6 */}
            <div className="bg-gradient-to-br from-cyan-50 to-white p-6 rounded-2xl ring-1 ring-cyan-100">
              <div className="w-12 h-12 bg-cyan-100 rounded-xl flex items-center justify-center mb-4">
                <svg className="w-6 h-6 text-cyan-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
                </svg>
              </div>
              <h3 className="text-lg font-bold text-gray-900 mb-2">모바일 최적화</h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                언제 어디서나 편리하게. 모바일에서도 완벽한 경매 경험을 제공합니다.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 이용 방법 섹션 */}
      <section className="py-20 sm:py-28 bg-gray-50">
        <div className="max-w-5xl mx-auto px-6">
          <div className="text-center mb-16">
            <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
              이용 방법
            </h2>
            <p className="text-gray-500 text-lg">
              간단한 3단계로 시작하세요
            </p>
          </div>

          {/* 모바일: 세로 배치 */}
          <div className="flex flex-col gap-8 md:hidden">
            {/* 단계 1 */}
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/25">
                <span className="text-2xl font-bold text-white">1</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">경매 등록</h3>
              <p className="text-gray-500 leading-relaxed">
                사진 찍고, 설명 쓰고, 시작가만 정하면 끝!<br />
                24시간 또는 48시간 경매가 시작됩니다.
              </p>
            </div>

            {/* 화살표 */}
            <div className="flex justify-center">
              <svg className="w-6 h-6 text-gray-300 rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
              </svg>
            </div>

            {/* 단계 2 */}
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/25">
                <span className="text-2xl font-bold text-white">2</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">실시간 입찰</h3>
              <p className="text-gray-500 leading-relaxed">
                구매자들이 실시간으로 경쟁 입찰!<br />
                가격이 올라가는 짜릿한 경험을 하세요.
              </p>
            </div>

            {/* 화살표 */}
            <div className="flex justify-center">
              <svg className="w-6 h-6 text-gray-300 rotate-90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
              </svg>
            </div>

            {/* 단계 3 */}
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/25">
                <span className="text-2xl font-bold text-white">3</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">낙찰 & 거래</h3>
              <p className="text-gray-500 leading-relaxed">
                최고 입찰자에게 낙찰!<br />
                직거래 또는 택배로 안전하게 거래하세요.
              </p>
            </div>
          </div>

          {/* PC: 가로 배치 */}
          <div className="hidden md:grid grid-cols-3 gap-8">
            {/* 단계 1 */}
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/25">
                <span className="text-2xl font-bold text-white">1</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">경매 등록</h3>
              <p className="text-gray-500 leading-relaxed text-sm">
                사진 찍고, 설명 쓰고, 시작가만 정하면 끝!<br />
                24시간 또는 48시간 경매가 시작됩니다.
              </p>
            </div>

            {/* 단계 2 */}
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/25">
                <span className="text-2xl font-bold text-white">2</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">실시간 입찰</h3>
              <p className="text-gray-500 leading-relaxed text-sm">
                구매자들이 실시간으로 경쟁 입찰!<br />
                가격이 올라가는 짜릿한 경험을 하세요.
              </p>
            </div>

            {/* 단계 3 */}
            <div className="text-center">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-violet-600 rounded-2xl flex items-center justify-center mx-auto mb-6 shadow-lg shadow-blue-500/25">
                <span className="text-2xl font-bold text-white">3</span>
              </div>
              <h3 className="text-xl font-bold text-gray-900 mb-3">낙찰 & 거래</h3>
              <p className="text-gray-500 leading-relaxed text-sm">
                최고 입찰자에게 낙찰!<br />
                직거래 또는 택배로 안전하게 거래하세요.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* 타겟 사용자 섹션 */}
      <section className="py-20 sm:py-28 bg-white">
        <div className="max-w-5xl mx-auto px-6">
          <div className="text-center mb-16">
            <h2 className="text-3xl sm:text-4xl font-bold text-gray-900 mb-4">
              이런 분들께 추천해요
            </h2>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            {/* 판매자 */}
            <div className="bg-gradient-to-br from-blue-500 to-violet-600 p-8 rounded-3xl text-white">
              <div className="w-14 h-14 bg-white/20 rounded-2xl flex items-center justify-center mb-6">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h3 className="text-2xl font-bold mb-4">판매자</h3>
              <ul className="space-y-3 text-white/90">
                <li className="flex items-start gap-3">
                  <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  <span>시세를 몰라서 가격 정하기 어려운 분</span>
                </li>
                <li className="flex items-start gap-3">
                  <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  <span>네고 스트레스 없이 팔고 싶은 분</span>
                </li>
                <li className="flex items-start gap-3">
                  <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  <span>적정가 이상 받고 싶은 분</span>
                </li>
              </ul>
            </div>

            {/* 구매자 */}
            <div className="bg-gray-900 p-8 rounded-3xl text-white">
              <div className="w-14 h-14 bg-white/10 rounded-2xl flex items-center justify-center mb-6">
                <svg className="w-7 h-7" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                </svg>
              </div>
              <h3 className="text-2xl font-bold mb-4">구매자</h3>
              <ul className="space-y-3 text-white/90">
                <li className="flex items-start gap-3">
                  <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  <span>좋은 물건을 합리적인 가격에 사고 싶은 분</span>
                </li>
                <li className="flex items-start gap-3">
                  <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  <span>경매의 짜릿함을 즐기고 싶은 분</span>
                </li>
                <li className="flex items-start gap-3">
                  <svg className="w-5 h-5 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                  <span>2순위 기회로 득템하고 싶은 분</span>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* 최종 CTA 섹션 */}
      <section className="py-20 sm:py-28 bg-gradient-to-br from-blue-500 to-violet-600">
        <div className="max-w-3xl mx-auto px-6 text-center">
          <h2 className="text-3xl sm:text-4xl font-bold text-white mb-6">
            지금 바로 시작하세요
          </h2>
          <p className="text-xl text-white/80 mb-10">
            호구 없는 경매, FairBid에서 경험해보세요
          </p>
          <Link
            to="/auctions"
            className="inline-block px-10 py-4 bg-white text-blue-600 text-lg font-bold rounded-2xl shadow-lg hover:shadow-xl transition-[transform,box-shadow] duration-300 hover:-translate-y-0.5"
          >
            경매 둘러보기
          </Link>
        </div>
      </section>

      {/* 푸터 */}
      <footer className="py-8 bg-gray-900 text-center">
        <p className="text-gray-400 text-sm">
          © 2026 FairBid. All rights reserved.
        </p>
      </footer>
    </div>
  );
}
