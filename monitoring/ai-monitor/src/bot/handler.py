"""비서 Bot의 질문 처리.

사용자 질문 수신 → Claude tool_use 루프 → 최종 답변.

흐름:
1. 사용자 질문 + (스레드면) 스레드 컨텍스트(연결된 알람 id)를 시스템 프롬프트에 주입
2. Claude에 tool 정의와 함께 호출
3. 응답이 tool_use 블록을 포함하면 해당 도구 실행 → 결과 재전달
4. stop_reason == "end_turn" 이면 최종 텍스트 반환

최대 5라운드 tool_use 허용 (무한 루프 방지).
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

from anthropic import Anthropic

from ..config import Settings
from ..state import StateStore
from .tools import TOOL_SCHEMAS, ToolExecutor

logger = logging.getLogger(__name__)

PROMPTS_DIR = Path(__file__).resolve().parent.parent / "prompts"
MAX_TOOL_ROUNDS = 5
# 스레드 내 이전 대화 최대 개수 (현재 질문은 별도 추가)
THREAD_CONTEXT_LIMIT = 10


class QuestionHandler:
    def __init__(self, settings: Settings, store: StateStore, executor: ToolExecutor):
        self.settings = settings
        self.store = store
        self.executor = executor
        self.client = Anthropic(api_key=settings.claude_api_key)
        self.system_prompt = (PROMPTS_DIR / "assistant.md").read_text(encoding="utf-8")

    async def answer(self, thread, question: str) -> str:
        """Discord 스레드에서 받은 질문에 답.

        내부 Claude 호출은 blocking이므로 asyncio.to_thread로 이벤트 루프 분리.
        스레드 히스토리 로드는 async라 to_thread 진입 전에 수행.
        """
        import asyncio

        history_messages: list[dict] = []
        if thread is not None:
            try:
                history_messages = await self._load_thread_context(thread)
            except Exception as e:
                logger.warning("thread history load failed: %s", e)

        return await asyncio.to_thread(
            self._answer_sync, thread, question, history_messages
        )

    async def _load_thread_context(self, thread) -> list[dict]:
        """스레드의 이전 대화를 Claude 메시지 포맷으로 변환.

        규칙:
        - Bot 메시지 → assistant role, 사용자 메시지 → user role
        - content 없는 메시지(임베드만, 시스템 알림) 스킵
        - 가장 최근 메시지(현재 질문 자체)는 제외
        - 연속 같은 role은 병합 (Claude API 요구사항)
        - 첫 메시지는 반드시 user (assistant로 시작하면 앞쪽 제거)
        """
        raw_msgs = []
        # limit+1: 가장 최근 1개는 현재 질문이라 제외하고 N개 확보
        async for m in thread.history(limit=THREAD_CONTEXT_LIMIT + 1, oldest_first=False):
            raw_msgs.append(m)
        if len(raw_msgs) <= 1:
            return []

        # 최신 1개(현재 질문) 제외, 오래된 것부터로 정렬
        raw_msgs = raw_msgs[1:]
        raw_msgs.reverse()

        bot_id = thread.guild.me.id if thread.guild else thread.me.id

        claude_msgs: list[dict] = []
        for m in raw_msgs:
            is_bot = m.author.id == bot_id
            role = "assistant" if is_bot else "user"
            content = (m.content or "").strip()
            if not is_bot and m.mentions:
                for mention in m.mentions:
                    content = content.replace(f"<@{mention.id}>", "").replace(f"<@!{mention.id}>", "")
                content = content.strip()
            if not content:
                continue
            # 연속 같은 role은 병합
            if claude_msgs and claude_msgs[-1]["role"] == role:
                claude_msgs[-1]["content"] += "\n\n" + content
            else:
                claude_msgs.append({"role": role, "content": content})

        # 첫 메시지는 user여야 함 (Claude API)
        while claude_msgs and claude_msgs[0]["role"] != "user":
            claude_msgs = claude_msgs[1:]

        return claude_msgs

    def _answer_sync(self, thread, question: str, history_messages: list[dict]) -> str:
        """실제 동기 구현 — Claude tool_use 루프.

        history_messages: async로 미리 로드된 스레드 이전 대화.
        """
        # 스레드가 알람 스레드인지 식별
        alert_context = ""
        if thread is not None:
            history = self.store.find_history_by_thread_id(str(thread.id))
            if history:
                alert_context = (
                    f"\n\n# 현재 대화 컨텍스트\n"
                    f"이 스레드는 알람 id={history['id']}에 연결된다.\n"
                    f"- rule_key: {history['rule_key']}\n"
                    f"- severity: {history['severity']}\n"
                    f"- kind: {history['kind']}\n"
                    f"- value: {history['value']}\n"
                    f"- fired_at: {history['fired_at']} (unix)\n"
                    f"필요하면 get_alert_report({history['id']})로 당시 분석을 확인해라."
                )

        # 이전 대화 + 현재 질문
        messages = list(history_messages) + [{"role": "user", "content": question}]
        system_with_context = self.system_prompt + alert_context

        for round_num in range(MAX_TOOL_ROUNDS):
            logger.debug("tool_use round %d", round_num)
            try:
                response = self.client.messages.create(
                    model=self.settings.runtime.claude_model,
                    max_tokens=1024,
                    system=system_with_context,
                    tools=TOOL_SCHEMAS,
                    messages=messages,
                )
            except Exception as e:
                logger.exception("Claude call failed: %s", e)
                return f"⚠️ Claude API 오류: {type(e).__name__}"

            if response.stop_reason == "end_turn":
                return _extract_text(response)

            if response.stop_reason == "tool_use":
                messages.append({"role": "assistant", "content": response.content})

                tool_results = []
                for block in response.content:
                    if getattr(block, "type", None) != "tool_use":
                        continue
                    tool_name = block.name
                    tool_input = block.input
                    logger.info("executing tool: %s(%s)", tool_name, tool_input)
                    result = self.executor.execute(tool_name, tool_input)
                    tool_results.append({
                        "type": "tool_result",
                        "tool_use_id": block.id,
                        "content": json.dumps(result, ensure_ascii=False, default=str),
                    })

                messages.append({"role": "user", "content": tool_results})
                continue

            logger.warning("unexpected stop_reason: %s", response.stop_reason)
            return _extract_text(response) or "⚠️ 응답 생성 실패"

        return "⚠️ 도구 호출 횟수 초과 (5회)"


def _extract_text(response) -> str:
    """Claude 응답에서 텍스트 블록만 추출."""
    return "".join(
        block.text for block in response.content
        if getattr(block, "type", None) == "text"
    ).strip()
