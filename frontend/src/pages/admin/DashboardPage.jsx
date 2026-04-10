import { useState, useEffect } from 'react';
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { getStatsOverview, getDailyAuctionStats, getTimePattern } from '../../api/admin';
import LoadingSpinner from '../../components/LoadingSpinner';

/**
 * 관리자 대시보드 페이지
 * 핵심 지표와 통계를 표시한다.
 */
export default function DashboardPage() {
  // 기간 선택 (7, 30, null=전체)
  const [days, setDays] = useState(7);

  // 통계 데이터 상태
  const [overview, setOverview] = useState(null);
  const [dailyStats, setDailyStats] = useState(null);
  const [timePattern, setTimePattern] = useState(null);

  // 로딩 및 에러 상태
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 데이터 로드
  useEffect(() => {
    async function fetchStats() {
      setLoading(true);
      setError(null);

      try {
        const [overviewData, dailyData, patternData] = await Promise.all([
          getStatsOverview(days),
          getDailyAuctionStats(days),
          getTimePattern(days),
        ]);

        setOverview(overviewData);
        setDailyStats(dailyData);
        setTimePattern(patternData);
      } catch (err) {
        console.error('통계 조회 실패:', err);
        setError(err.message || '통계를 불러오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    }

    fetchStats();
  }, [days]);

  // 일별 차트 데이터 포맷팅
  const dailyChartData =
    dailyStats?.dailyStats?.map((item) => ({
      date: formatDate(item.date),
      신규경매: item.newAuctions,
      낙찰완료: item.completedAuctions,
      입찰수: item.bids,
    })) || [];

  // 시간대별 차트 데이터 포맷팅
  const hourlyChartData =
    timePattern?.hourlyBidCounts?.map((item) => ({
      hour: `${item.hour}시`,
      입찰수: item.count,
    })) || [];

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">대시보드</h1>
          <p className="text-sm text-gray-500 mt-1">FairBid 운영 현황을 한눈에 확인하세요</p>
        </div>

        {/* 기간 선택 */}
        <div className="flex gap-2">
          {[
            { value: 7, label: '7일' },
            { value: 30, label: '30일' },
            { value: null, label: '전체' },
          ].map((option) => (
            <button
              key={option.label}
              onClick={() => setDays(option.value)}
              className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
                days === option.value
                  ? 'bg-indigo-600 text-white'
                  : 'bg-white text-gray-600 ring-1 ring-gray-200 hover:bg-gray-50'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      {/* 에러 표시 */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
        </div>
      )}

      {/* 통계 카드 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        <StatCard
          label="총 경매 수"
          value={loading ? '-' : (overview?.totalAuctions ?? 0)}
          unit="건"
          loading={loading}
        />
        <StatCard
          label="낙찰률"
          value={loading ? '-' : (overview?.completedRate ?? 0)}
          unit="%"
          loading={loading}
        />
        <StatCard
          label="평균 경쟁률"
          value={loading ? '-' : (overview?.avgBidCount ?? 0)}
          unit="명"
          loading={loading}
        />
        <StatCard
          label="평균 상승률"
          value={loading ? '-' : (overview?.avgPriceIncreaseRate ?? 0)}
          unit="%"
          loading={loading}
        />
        <StatCard
          label="연장 발생률"
          value={loading ? '-' : (overview?.extensionRate ?? 0)}
          unit="%"
          loading={loading}
        />
      </div>

      {/* 차트 영역 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* 일별 경매 현황 차트 */}
        <div className="bg-white rounded-2xl p-6 shadow-sm ring-1 ring-gray-100">
          <h3 className="text-sm font-semibold text-gray-700 mb-4">일별 경매 현황</h3>
          {loading && (
            <div className="h-64 flex items-center justify-center">
              <LoadingSpinner />
            </div>
          )}
          {!loading && dailyChartData.length === 0 && (
            <div className="h-64 flex items-center justify-center text-gray-400 text-sm">
              데이터가 없습니다
            </div>
          )}
          {!loading && dailyChartData.length > 0 && (
            <ResponsiveContainer width="100%" height={256}>
              <LineChart data={dailyChartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis
                  dataKey="date"
                  tick={{ fontSize: 12 }}
                  tickLine={false}
                  axisLine={{ stroke: '#e5e7eb' }}
                />
                <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={{ stroke: '#e5e7eb' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#fff',
                    border: '1px solid #e5e7eb',
                    borderRadius: '8px',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                  }}
                />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="신규경매"
                  stroke="#6366f1"
                  strokeWidth={2}
                  dot={{ fill: '#6366f1', strokeWidth: 0, r: 3 }}
                  activeDot={{ r: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="낙찰완료"
                  stroke="#10b981"
                  strokeWidth={2}
                  dot={{ fill: '#10b981', strokeWidth: 0, r: 3 }}
                  activeDot={{ r: 5 }}
                />
                <Line
                  type="monotone"
                  dataKey="입찰수"
                  stroke="#f59e0b"
                  strokeWidth={2}
                  dot={{ fill: '#f59e0b', strokeWidth: 0, r: 3 }}
                  activeDot={{ r: 5 }}
                />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>

        {/* 시간대별 입찰 분포 차트 */}
        <div className="bg-white rounded-2xl p-6 shadow-sm ring-1 ring-gray-100">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-gray-700">시간대별 입찰 분포</h3>
            {timePattern && (
              <span className="text-xs text-indigo-600 font-medium">
                피크: {timePattern.peakHour}시 ({timePattern.peakCount}건)
              </span>
            )}
          </div>
          {loading && (
            <div className="h-64 flex items-center justify-center">
              <LoadingSpinner />
            </div>
          )}
          {!loading && hourlyChartData.length === 0 && (
            <div className="h-64 flex items-center justify-center text-gray-400 text-sm">
              데이터가 없습니다
            </div>
          )}
          {!loading && hourlyChartData.length > 0 && (
            <ResponsiveContainer width="100%" height={256}>
              <BarChart data={hourlyChartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                <XAxis
                  dataKey="hour"
                  tick={{ fontSize: 10 }}
                  tickLine={false}
                  axisLine={{ stroke: '#e5e7eb' }}
                  interval={2}
                />
                <YAxis tick={{ fontSize: 12 }} tickLine={false} axisLine={{ stroke: '#e5e7eb' }} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#fff',
                    border: '1px solid #e5e7eb',
                    borderRadius: '8px',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                  }}
                />
                <Bar dataKey="입찰수" fill="#6366f1" radius={[4, 4, 0, 0]} maxBarSize={24} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * 통계 카드 컴포넌트
 */
function StatCard({ label, value, unit, loading }) {
  return (
    <div className="bg-white rounded-2xl p-5 shadow-sm ring-1 ring-gray-100">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{label}</p>
      <p className="text-3xl font-bold text-gray-900 mt-2">
        {loading ? (
          <span className="inline-block w-16 h-8 bg-gray-100 rounded animate-pulse" />
        ) : (
          <>
            {typeof value === 'number' ? value.toLocaleString() : value}
            <span className="text-lg font-medium text-gray-400 ml-1">{unit}</span>
          </>
        )}
      </p>
    </div>
  );
}

/**
 * 날짜 포맷팅 (MM/DD)
 * @param {string} dateStr - ISO 날짜 문자열
 * @returns {string}
 */
function formatDate(dateStr) {
  const date = new Date(dateStr);
  return `${date.getMonth() + 1}/${date.getDate()}`;
}
