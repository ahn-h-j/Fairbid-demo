import { useState, useRef, useEffect } from 'react';
import Spinner from './Spinner';

/**
 * Cloudinary 이미지 업로드 컴포넌트
 *
 * @param {object} props
 * @param {string[]} props.images - 업로드된 이미지 URL 배열
 * @param {function} props.onChange - 이미지 배열 변경 시 호출되는 콜백
 * @param {number} [props.maxImages=5] - 최대 업로드 가능 이미지 수
 * @param {function} [props.onUploadingChange] - 업로드 상태 변경 시 호출되는 콜백
 */
export default function ImageUpload({ images = [], onChange, maxImages = 5, onUploadingChange }) {
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [error, setError] = useState(null);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef(null);
  // stale closure 방지를 위한 최신 images 참조
  const imagesRef = useRef(images);

  // images 변경 시 ref 동기화
  useEffect(() => {
    imagesRef.current = images;
  }, [images]);

  // Cloudinary 설정
  const cloudName = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME;
  const uploadPreset = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET;

  /**
   * 파일 업로드 처리
   * @param {FileList} files
   */
  const handleUpload = async (files) => {
    if (!cloudName || !uploadPreset) {
      setError('Cloudinary 설정이 필요합니다.');
      return;
    }

    const fileArray = Array.from(files);
    const remainingSlots = maxImages - imagesRef.current.length;

    if (remainingSlots <= 0) {
      setError(`최대 ${maxImages}장까지 업로드 가능합니다.`);
      return;
    }

    // 이미지 파일만 먼저 필터링 후 슬롯 수만큼 선택
    const imageFiles = fileArray
      .filter((file) => file.type.startsWith('image/'))
      .slice(0, remainingSlots);

    if (imageFiles.length === 0) {
      setError('이미지 파일만 업로드 가능합니다.');
      return;
    }

    // 파일 크기 검증 (10MB 제한)
    const maxSize = 10 * 1024 * 1024;
    const oversizedFiles = imageFiles.filter((file) => file.size > maxSize);
    if (oversizedFiles.length > 0) {
      setError('파일 크기는 10MB 이하여야 합니다.');
      return;
    }

    setError(null);
    setUploading(true);
    onUploadingChange?.(true);
    setUploadProgress(0);

    const uploadedUrls = [];
    const totalFiles = imageFiles.length;

    for (let i = 0; i < totalFiles; i++) {
      const file = imageFiles[i];
      const formData = new FormData();
      formData.append('file', file);
      formData.append('upload_preset', uploadPreset);
      formData.append('folder', 'fairbid/auctions');

      try {
        const response = await fetch(
          `https://api.cloudinary.com/v1_1/${cloudName}/image/upload`,
          {
            method: 'POST',
            body: formData,
          }
        );

        if (!response.ok) {
          throw new Error('업로드 실패');
        }

        const data = await response.json();
        uploadedUrls.push(data.secure_url);
        setUploadProgress(Math.round(((i + 1) / totalFiles) * 100));
      } catch (err) {
        console.error('Upload error:', err);
        setError('이미지 업로드에 실패했습니다.');
        break;
      }
    }

    setUploading(false);
    onUploadingChange?.(false);
    setUploadProgress(0);

    if (uploadedUrls.length > 0) {
      onChange([...imagesRef.current, ...uploadedUrls]);
    }
  };

  /**
   * 이미지 삭제
   * @param {number} index
   */
  const handleRemove = (index) => {
    const newImages = images.filter((_, i) => i !== index);
    onChange(newImages);
  };

  /**
   * 파일 선택 버튼 클릭
   */
  const handleClick = () => {
    fileInputRef.current?.click();
  };

  /**
   * 파일 입력 변경
   */
  const handleFileChange = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      handleUpload(e.target.files);
    }
    // input 초기화 (같은 파일 재선택 가능하도록)
    e.target.value = '';
  };

  /**
   * 드래그 앤 드롭 핸들러
   */
  const handleDragOver = (e) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setDragOver(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleUpload(e.dataTransfer.files);
    }
  };

  const canUploadMore = images.length < maxImages;

  return (
    <div className="space-y-3">
      {/* 에러 메시지 */}
      {error ? (
        <div className="text-[12px] text-red-500 bg-red-50 px-3 py-2 rounded-lg">
          {error}
        </div>
      ) : null}

      {/* 업로드 영역 */}
      {canUploadMore ? (
        <div
          onClick={handleClick}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          className={`relative border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-colors duration-200 ${
            dragOver
              ? 'border-blue-400 bg-blue-50'
              : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
          } ${uploading ? 'pointer-events-none opacity-60' : ''}`}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleClick();
            }
          }}
          aria-label="이미지 업로드 영역"
        >
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            multiple
            onChange={handleFileChange}
            className="hidden"
            aria-hidden="true"
          />

          {uploading ? (
            <div className="flex flex-col items-center gap-2">
              <Spinner size="md" />
              <span className="text-[13px] text-gray-500">
                업로드 중… {uploadProgress}%
              </span>
            </div>
          ) : (
            <div className="flex flex-col items-center gap-2">
              <svg
                className="w-10 h-10 text-gray-300"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={1.5}
                  d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
                />
              </svg>
              <div>
                <p className="text-[13px] font-medium text-gray-600">
                  클릭하거나 드래그하여 이미지 업로드
                </p>
                <p className="text-[11px] text-gray-400 mt-1">
                  최대 {maxImages}장, 10MB 이하
                </p>
              </div>
            </div>
          )}
        </div>
      ) : null}

      {/* 업로드 카운터 */}
      <div className="text-[12px] text-gray-400">
        {images.length} / {maxImages}장
      </div>

      {/* 이미지 미리보기 */}
      {images.length > 0 ? (
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
          {images.map((url, index) => (
            <div
              key={url}
              className="relative aspect-square rounded-lg overflow-hidden bg-gray-100 group"
            >
              <img
                src={url}
                alt={`상품 이미지 ${index + 1}`}
                className="w-full h-full object-cover"
                loading="lazy"
              />
              {/* 삭제 버튼 */}
              <button
                type="button"
                onClick={() => handleRemove(index)}
                className="absolute top-1 right-1 w-6 h-6 bg-black/60 hover:bg-black/80 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                aria-label={`이미지 ${index + 1} 삭제`}
              >
                <svg
                  className="w-3.5 h-3.5 text-white"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </button>
              {/* 첫 번째 이미지 표시 */}
              {index === 0 ? (
                <div className="absolute bottom-1 left-1 px-1.5 py-0.5 bg-blue-500 text-white text-[10px] font-medium rounded">
                  대표
                </div>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
