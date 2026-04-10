import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiRequest } from '../api/client';
import { formatPhoneInput } from '../utils/formatters';
import Spinner from '../components/Spinner';

/**
 * 마이페이지
 * 프로필 + 거래 통계 + 배송지 관리
 */
export default function MyPage() {
  const { user, updateAuthFromToken, logout } = useAuth();
  const navigate = useNavigate();

  const [isEditingNickname, setIsEditingNickname] = useState(false);
  const [editNickname, setEditNickname] = useState('');
  const [nicknameError, setNicknameError] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);

  // 배송지 수정 상태
  const [isEditingAddress, setIsEditingAddress] = useState(false);
  const [addressForm, setAddressForm] = useState({
    recipientName: '',
    recipientPhone: '',
    postalCode: '',
    address: '',
    addressDetail: '',
  });
  const [addressError, setAddressError] = useState('');

  // 계좌 수정 상태
  const [isEditingBank, setIsEditingBank] = useState(false);
  const [bankForm, setBankForm] = useState({
    bankName: '',
    accountNumber: '',
    accountHolder: '',
  });
  const [bankError, setBankError] = useState('');

  const nicknameInputRef = useRef(null);

  // 프로필 정보 로드
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const data = await apiRequest('/users/me');
        setProfile(data);
        // 배송지 정보가 있으면 폼에 설정
        if (data.shippingAddress) {
          setAddressForm(data.shippingAddress);
        }
        // 계좌 정보가 있으면 폼에 설정
        if (data.bankAccount) {
          setBankForm(data.bankAccount);
        }
      } catch {
        // 프로필 로드 실패 시 기본값 사용
      } finally {
        setProfileLoading(false);
      }
    };
    loadProfile();
  }, []);

  /** 닉네임 수정 시작 */
  const startEditNickname = () => {
    setEditNickname(profile?.nickname || user?.nickname || '');
    setNicknameError('');
    setIsEditingNickname(true);
    setTimeout(() => nicknameInputRef.current?.focus(), 0);
  };

  /** 닉네임 수정 저장 */
  const saveNickname = async () => {
    const trimmed = editNickname.trim();
    if (trimmed.length < 2 || trimmed.length > 20) {
      setNicknameError('닉네임은 2~20자로 입력해주세요.');
      return;
    }

    try {
      const result = await apiRequest('/users/me', {
        method: 'PUT',
        body: JSON.stringify({ nickname: trimmed }),
      });

      if (result.accessToken) {
        updateAuthFromToken(result.accessToken);
      }
      setProfile((prev) => (prev ? { ...prev, nickname: trimmed } : prev));
      setIsEditingNickname(false);
    } catch (err) {
      if (err.code === 'NICKNAME_DUPLICATE') {
        setNicknameError('이미 사용 중인 닉네임입니다.');
      } else {
        setNicknameError(err.message || '수정에 실패했습니다.');
      }
    }
  };

  /** 닉네임 수정 키보드 핸들러 */
  const handleNicknameKeyDown = (e) => {
    if (e.key === 'Enter') saveNickname();
    if (e.key === 'Escape') setIsEditingNickname(false);
  };

  /** 배송지 저장 */
  const saveAddress = async () => {
    if (!addressForm.recipientName || !addressForm.recipientPhone || !addressForm.address) {
      setAddressError('수령인, 연락처, 주소는 필수입니다.');
      return;
    }

    try {
      await apiRequest('/users/me/shipping-address', {
        method: 'PUT',
        body: JSON.stringify(addressForm),
      });
      setProfile((prev) => (prev ? { ...prev, shippingAddress: addressForm } : prev));
      setIsEditingAddress(false);
      setAddressError('');
    } catch (err) {
      setAddressError(err.message || '저장에 실패했습니다.');
    }
  };

  /** 계좌 저장 */
  const saveBankAccount = async () => {
    if (!bankForm.bankName || !bankForm.accountNumber || !bankForm.accountHolder) {
      setBankError('은행명, 계좌번호, 예금주는 필수입니다.');
      return;
    }

    try {
      await apiRequest('/users/me/bank-account', {
        method: 'PUT',
        body: JSON.stringify(bankForm),
      });
      setProfile((prev) => (prev ? { ...prev, bankAccount: bankForm } : prev));
      setIsEditingBank(false);
      setBankError('');
    } catch (err) {
      setBankError(err.message || '저장에 실패했습니다.');
    }
  };

  /** 회원 탈퇴 처리 */
  const handleDeleteAccount = async () => {
    setDeleteError('');
    try {
      await apiRequest('/users/me', { method: 'DELETE' });
      setShowDeleteModal(false);
      await logout();
      navigate('/auctions', { replace: true });
    } catch (err) {
      setDeleteError(err.message || '탈퇴에 실패했습니다. 잠시 후 다시 시도해주세요.');
    }
  };

  /** 가격 포맷팅 */
  const formatPrice = (price) => {
    return new Intl.NumberFormat('ko-KR').format(price || 0);
  };

  if (profileLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* 프로필 섹션 */}
      <section className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
        <h2 className="text-lg font-bold text-gray-900 mb-4">프로필</h2>
        <dl className="space-y-3">
          {/* 닉네임 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">닉네임</dt>
            <dd className="flex items-center gap-2">
              {isEditingNickname ? (
                <div className="flex items-center gap-1.5">
                  <input
                    ref={nicknameInputRef}
                    type="text"
                    value={editNickname}
                    onChange={(e) => {
                      setEditNickname(e.target.value);
                      setNicknameError('');
                    }}
                    onKeyDown={handleNicknameKeyDown}
                    maxLength={20}
                    spellCheck={false}
                    className="w-32 px-2 py-1 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                  />
                  <button
                    type="button"
                    onClick={saveNickname}
                    className="px-2 py-1 text-xs font-semibold text-blue-600 hover:bg-blue-50 rounded-lg"
                  >
                    저장
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsEditingNickname(false)}
                    className="px-2 py-1 text-xs font-semibold text-gray-500 hover:bg-gray-100 rounded-lg"
                  >
                    취소
                  </button>
                </div>
              ) : (
                <>
                  <span className="text-sm font-semibold text-gray-900">
                    {profile?.nickname || user?.nickname}
                  </span>
                  <button
                    type="button"
                    onClick={startEditNickname}
                    className="text-xs text-blue-500 hover:text-blue-700"
                  >
                    수정
                  </button>
                </>
              )}
            </dd>
          </div>
          {nicknameError && <p className="text-xs text-red-600 text-right">{nicknameError}</p>}

          {/* 이메일 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">이메일</dt>
            <dd className="text-sm text-gray-900">{profile?.email || '-'}</dd>
          </div>

          {/* 전화번호 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">전화번호</dt>
            <dd className="text-sm text-gray-900">{profile?.phoneNumber || '-'}</dd>
          </div>

          {/* 경고 횟수 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">노쇼 경고</dt>
            <dd className="text-sm font-semibold">
              <span className={profile?.warningCount > 0 ? 'text-red-600' : 'text-gray-900'}>
                {profile?.warningCount ?? 0}/3
              </span>
            </dd>
          </div>
        </dl>
      </section>

      {/* 거래 통계 섹션 */}
      <section className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
        <h2 className="text-lg font-bold text-gray-900 mb-4">거래 통계</h2>
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-blue-50 rounded-xl p-4 text-center">
            <p className="text-2xl font-bold text-blue-600">{profile?.stats?.totalSales ?? 0}</p>
            <p className="text-xs text-gray-500 mt-1">판매 완료</p>
          </div>
          <div className="bg-violet-50 rounded-xl p-4 text-center">
            <p className="text-2xl font-bold text-violet-600">
              {profile?.stats?.totalPurchases ?? 0}
            </p>
            <p className="text-xs text-gray-500 mt-1">구매 완료</p>
          </div>
          <div className="bg-blue-50 rounded-xl p-4 text-center">
            <p className="text-2xl font-bold text-blue-600">
              {formatPrice(profile?.stats?.totalSalesAmount)}원
            </p>
            <p className="text-xs text-gray-500 mt-1">판매 금액</p>
          </div>
          <div className="bg-violet-50 rounded-xl p-4 text-center">
            <p className="text-2xl font-bold text-violet-600">
              {formatPrice(profile?.stats?.totalPurchaseAmount)}원
            </p>
            <p className="text-xs text-gray-500 mt-1">구매 금액</p>
          </div>
        </div>
      </section>

      {/* 배송지 관리 섹션 */}
      <section className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-gray-900">배송지</h2>
          {!isEditingAddress && (
            <button
              type="button"
              onClick={() => setIsEditingAddress(true)}
              className="text-xs text-blue-500 hover:text-blue-700"
            >
              {profile?.shippingAddress ? '수정' : '등록'}
            </button>
          )}
        </div>

        {isEditingAddress && (
          <div className="space-y-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">수령인</label>
              <input
                type="text"
                value={addressForm.recipientName}
                onChange={(e) => setAddressForm({ ...addressForm, recipientName: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="홍길동"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">연락처</label>
              <input
                type="tel"
                value={addressForm.recipientPhone}
                onChange={(e) =>
                  setAddressForm({
                    ...addressForm,
                    recipientPhone: formatPhoneInput(e.target.value),
                  })
                }
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="010-1234-5678"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">우편번호</label>
              <input
                type="text"
                value={addressForm.postalCode}
                onChange={(e) => setAddressForm({ ...addressForm, postalCode: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="12345"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">주소</label>
              <input
                type="text"
                value={addressForm.address}
                onChange={(e) => setAddressForm({ ...addressForm, address: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="서울시 강남구 테헤란로 123"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">상세주소</label>
              <input
                type="text"
                value={addressForm.addressDetail}
                onChange={(e) => setAddressForm({ ...addressForm, addressDetail: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="101동 202호"
              />
            </div>
            {addressError && <p className="text-xs text-red-600">{addressError}</p>}
            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={saveAddress}
                className="flex-1 py-2 text-sm font-semibold text-white bg-blue-500 rounded-lg hover:bg-blue-600"
              >
                저장
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsEditingAddress(false);
                  setAddressError('');
                  // 저장된 배송지가 있으면 복원, 없으면 폼 초기화
                  if (profile?.shippingAddress) {
                    setAddressForm(profile.shippingAddress);
                  } else {
                    setAddressForm({
                      recipientName: '',
                      recipientPhone: '',
                      postalCode: '',
                      address: '',
                      addressDetail: '',
                    });
                  }
                }}
                className="flex-1 py-2 text-sm font-semibold text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
              >
                취소
              </button>
            </div>
          </div>
        )}
        {!isEditingAddress && profile?.shippingAddress && (
          <div className="text-sm text-gray-700 space-y-1">
            <p className="font-semibold">
              {profile.shippingAddress.recipientName} ({profile.shippingAddress.recipientPhone})
            </p>
            <p>
              {profile.shippingAddress.postalCode && `[${profile.shippingAddress.postalCode}] `}
              {profile.shippingAddress.address}
            </p>
            {profile.shippingAddress.addressDetail && (
              <p>{profile.shippingAddress.addressDetail}</p>
            )}
          </div>
        )}
        {!isEditingAddress && !profile?.shippingAddress && (
          <p className="text-sm text-gray-400">등록된 배송지가 없습니다.</p>
        )}
      </section>

      {/* 계좌 관리 섹션 */}
      <section className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold text-gray-900">판매 계좌</h2>
          {!isEditingBank && (
            <button
              type="button"
              onClick={() => setIsEditingBank(true)}
              className="text-xs text-blue-500 hover:text-blue-700"
            >
              {profile?.bankAccount ? '수정' : '등록'}
            </button>
          )}
        </div>

        {isEditingBank && (
          <div className="space-y-3">
            <div>
              <label className="block text-xs text-gray-500 mb-1">은행명</label>
              <input
                type="text"
                value={bankForm.bankName}
                onChange={(e) => setBankForm({ ...bankForm, bankName: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="카카오뱅크"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">계좌번호</label>
              <input
                type="text"
                value={bankForm.accountNumber}
                onChange={(e) => setBankForm({ ...bankForm, accountNumber: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="3333-12-3456789"
              />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">예금주</label>
              <input
                type="text"
                value={bankForm.accountHolder}
                onChange={(e) => setBankForm({ ...bankForm, accountHolder: e.target.value })}
                className="w-full px-3 py-2 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                placeholder="홍길동"
              />
            </div>
            {bankError && <p className="text-xs text-red-600">{bankError}</p>}
            <div className="flex gap-2 pt-2">
              <button
                type="button"
                onClick={saveBankAccount}
                className="flex-1 py-2 text-sm font-semibold text-white bg-blue-500 rounded-lg hover:bg-blue-600"
              >
                저장
              </button>
              <button
                type="button"
                onClick={() => {
                  setIsEditingBank(false);
                  setBankError('');
                  if (profile?.bankAccount) {
                    setBankForm(profile.bankAccount);
                  } else {
                    setBankForm({ bankName: '', accountNumber: '', accountHolder: '' });
                  }
                }}
                className="flex-1 py-2 text-sm font-semibold text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
              >
                취소
              </button>
            </div>
          </div>
        )}
        {!isEditingBank && profile?.bankAccount && (
          <div className="text-sm text-gray-700 space-y-1">
            <p className="font-semibold">{profile.bankAccount.bankName}</p>
            <p>{profile.bankAccount.accountNumber}</p>
            <p className="text-gray-500">{profile.bankAccount.accountHolder}</p>
          </div>
        )}
        {!isEditingBank && !profile?.bankAccount && (
          <p className="text-sm text-gray-400">
            등록된 계좌가 없습니다. 판매 시 구매자에게 계좌를 알려주려면 등록해주세요.
          </p>
        )}
      </section>

      {/* 로그아웃 & 회원 탈퇴 */}
      <div className="flex flex-col items-center gap-3 pb-8">
        <button
          type="button"
          onClick={async () => {
            await logout();
            navigate('/auctions', { replace: true });
          }}
          className="px-6 py-2.5 text-sm font-semibold text-gray-600 bg-gray-100 rounded-xl hover:bg-gray-200 transition-colors"
        >
          로그아웃
        </button>
        <button
          type="button"
          onClick={() => setShowDeleteModal(true)}
          className="text-xs text-gray-400 hover:text-red-500 transition-colors"
        >
          회원 탈퇴
        </button>
      </div>

      {/* 탈퇴 확인 모달 */}
      {showDeleteModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-4">
          <div className="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <h3 className="text-lg font-bold text-gray-900 mb-2">회원 탈퇴</h3>
            <p className="text-sm text-gray-500 mb-4">
              정말 탈퇴하시겠습니까? 탈퇴 후 계정을 복구할 수 없습니다.
            </p>
            {deleteError && <p className="text-xs text-red-600 mb-4">{deleteError}</p>}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteModal(false)}
                className="flex-1 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 rounded-xl hover:bg-gray-200"
              >
                취소
              </button>
              <button
                type="button"
                onClick={handleDeleteAccount}
                className="flex-1 py-2.5 text-sm font-semibold text-white bg-red-500 rounded-xl hover:bg-red-600"
              >
                탈퇴하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
