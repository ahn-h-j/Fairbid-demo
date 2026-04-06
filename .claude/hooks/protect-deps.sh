#!/bin/bash
# ================================================================
# 의존성 추가 보호 hook
# Bash에서 npm install / yarn add 감지 시 exit 1 (사용자 승인 요청)
# 실패 시 docs/harness/feedback-log.md에 자동 기록
# ================================================================

CMD="$TOOL_INPUT_command"
REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")
LOG_FILE="$REPO_ROOT/docs/harness/feedback-log.md"

if echo "$CMD" | grep -qE "npm install |npm add |yarn add "; then
    TS=$(date "+%Y-%m-%d %H:%M")
    {
        echo ""
        echo "### $TS"
        echo "- **단계**: Claude Code hook"
        echo "- **도구**: Bash"
        echo "- **위반**: 새 의존성 추가 시도"
        echo "- **명령어**: $CMD"
        echo "- **상태**: open"
    } >> "$LOG_FILE"
    echo "새 npm 패키지 설치 감지: $CMD"
    echo "기존 라이브러리로 해결할 수 없나요? 정말 새 의존성이 필요한가요?"
    exit 1
fi

exit 0
