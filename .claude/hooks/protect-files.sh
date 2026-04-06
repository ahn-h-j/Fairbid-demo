#!/bin/bash
# ================================================================
# No-touch 파일 보호 hook
# Edit/Write 시 보호 대상 파일이면 exit 1 (사용자 승인 요청)
# 실패 시 docs/harness/feedback-log.md에 자동 기록
# ================================================================

FILE="$TOOL_INPUT_FILE_PATH$TOOL_INPUT_file_path"
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")
LOG_FILE="$REPO_ROOT/docs/harness/feedback-log.md"

# 보호 대상: 인증/보안 핵심 모듈
PROTECTED_PATTERNS="SecurityConfig|JwtTokenProvider|JwtProperties|RedisSentinelConfig|CookieUtils"

# 보호 대상: 핵심 설정 파일
PROTECTED_CONFIGS="application[.]yml|application-sentinel[.]yml|docker-compose[.]yml|Dockerfile|[.]github/workflows/"

log_failure() {
    local REASON="$1"
    local TS
    TS=$(date "+%Y-%m-%d %H:%M")
    {
        echo ""
        echo "### $TS"
        echo "- **단계**: Claude Code hook"
        echo "- **도구**: Edit/Write"
        echo "- **위반**: $REASON"
        echo "- **파일**: $FILE"
        echo "- **상태**: open"
    } >> "$LOG_FILE"
}

if echo "$FILE" | grep -qE "$PROTECTED_PATTERNS"; then
    log_failure "보호된 인증/보안 파일 수정 시도"
    echo "보호된 파일입니다: $FILE"
    echo "이 파일은 인증/보안 핵심 모듈입니다. 정말 수정이 필요한가요?"
    exit 1
fi

if echo "$FILE" | grep -qE "$PROTECTED_CONFIGS"; then
    log_failure "핵심 설정 파일 수정 시도"
    echo "핵심 설정 파일입니다: $FILE"
    echo "설정 변경은 의도치 않은 장애를 유발할 수 있습니다. 정말 수정이 필요한가요?"
    exit 1
fi

exit 0
