#!/bin/bash
# ================================================================
# sed -i 백업 확장자 누락 방지 hook
#
# Windows Git Bash(mingw)에서 `sed -i` 는 atomic rename 실패 시
# 원본 파일을 0바이트로 덮어쓰는 버그가 있음 — 복구 불가.
# 실제 이 레포에서 발생한 사고로 문서 1500줄이 날아간 적 있음.
#
# 차단:
#   - `sed -i '...'  file`   (-i 뒤에 공백, 백업 확장자 없음)
#   - `sed -i$`              (-i 뒤 끝)
#   - `sed --in-place ...`
#   - `sed --in-place$`
#
# 허용:
#   - `sed -i.bak '...' file`   (백업 확장자 명시)
#   - `sed -i'.bak' '...' file`
#   - `sed -i".orig" ...`
#
# 실패 시 docs/harness/feedback-log.md 에 자동 기록.
# ================================================================

CMD="$TOOL_INPUT_command"

if echo "$CMD" | grep -qE '(^|[^a-zA-Z_.-])sed[[:space:]]+(-i[[:space:]]|-i$|--in-place[[:space:]]|--in-place$)'; then
    REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || echo ".")
    LOG_FILE="$REPO_ROOT/docs/harness/feedback-log.md"
    TS=$(date "+%Y-%m-%d %H:%M")
    {
        echo ""
        echo "### $TS"
        echo "- **단계**: Claude Code hook"
        echo "- **도구**: Bash"
        echo "- **위반**: sed -i 백업 확장자 누락 (Windows Git Bash에서 파일 손상 위험)"
        echo "- **명령어**: $CMD"
        echo "- **상태**: open"
    } >> "$LOG_FILE"

    echo "=============================================================="
    echo "차단: sed -i 는 백업 확장자 없이 사용 금지"
    echo "=============================================================="
    echo "시도한 명령: $CMD"
    echo ""
    echo "이유: Windows Git Bash(mingw)에서 sed -i 는 atomic rename 실패"
    echo "      시 원본 파일을 0바이트로 만드는 버그가 있음."
    echo "      실제로 이 레포에서 문서 1500줄 삭제된 사고 발생."
    echo ""
    echo "대안:"
    echo "  1) 백업 확장자 명시: sed -i.bak 's/foo/bar/' file.txt"
    echo "  2) Edit 도구 사용 (작은 변경)"
    echo "  3) 임시 파일에 출력 후 mv: sed '...' file > file.tmp && mv file.tmp file"
    exit 1
fi

exit 0
