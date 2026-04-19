"""Discord Bot 클라이언트.

- 알람 발송 경로를 webhook → Bot API로 전환
- 알람 메시지 아래에 자동 스레드 생성 → 대화창 확보
- 스레드 내 멘션 또는 /ask 슬래시 명령으로 질의 응답

주 이벤트 루프는 asyncio. main.py가 이 Bot을 기동하고
주기 태스크(폴링/야간/주간)를 같은 루프에 create_task로 등록.
"""
from __future__ import annotations

import logging
from typing import Callable, Optional

import discord
from discord.ext import commands

logger = logging.getLogger(__name__)


class AiMonitorBot(commands.Bot):
    """ai-monitor 전용 Discord Bot.

    - commands.Bot 상속 — slash command (app_commands) 지원
    - alerts 채널에 메시지 + 스레드 발송 유틸 제공
    - on_message에서 스레드 내 질문을 handler로 위임
    """

    def __init__(self, channel_id: int, question_handler: Optional[Callable] = None):
        intents = discord.Intents.default()
        intents.message_content = True  # 스레드 내 자연어 멘션 질문 읽기용
        super().__init__(command_prefix="!", intents=intents)

        self.channel_id = channel_id
        self.question_handler = question_handler  # async (thread, question) -> str

    async def on_ready(self):
        logger.info("Bot logged in as %s (id=%s)", self.user, self.user.id)
        try:
            synced = await self.tree.sync()
            logger.info("slash commands synced: %d", len(synced))
        except Exception as e:
            logger.warning("slash command sync failed: %s", e)

    async def on_message(self, message: discord.Message):
        """스레드 안에서 Bot이 멘션되면 질문 핸들러 호출."""
        # 자기 자신은 무시 (루프 방지)
        if message.author == self.user:
            return
        # 스레드가 아니면 무시 (/ask 슬래시로 처리)
        if not isinstance(message.channel, discord.Thread):
            return
        # Bot이 직접 멘션되지 않으면 무시 (조용함 유지)
        if self.user not in message.mentions:
            return
        if self.question_handler is None:
            logger.warning("message received but no question_handler set")
            return

        # 멘션 텍스트 제거
        content = message.content
        for m in message.mentions:
            content = content.replace(f"<@{m.id}>", "").replace(f"<@!{m.id}>", "")
        question = content.strip()
        if not question:
            return

        try:
            async with message.channel.typing():
                answer = await self.question_handler(message.channel, question)
            await _send_long(message.channel, answer)
        except Exception as e:
            logger.exception("question handler failed: %s", e)
            await message.channel.send(f"⚠️ 답변 생성 실패: {type(e).__name__}")

    # === 알람 발송 유틸 ===

    async def send_alert(
        self,
        embed: discord.Embed,
        create_thread: bool = False,
        thread_name: str = "",
    ) -> tuple[Optional[str], Optional[str]]:
        """메인 채널에 알람 임베드를 발송한다.

        Args:
            embed: 알람 내용
            create_thread: True면 메시지 아래에 스레드 생성 (신규/악화만 권장)
            thread_name: 스레드 이름 (100자 제한)

        Returns:
            (message_id, thread_id) — state.attach_discord_ids 에 저장용
        """
        channel = self.get_channel(self.channel_id)
        if channel is None:
            logger.error("channel %s not found (Bot not invited?)", self.channel_id)
            return (None, None)

        try:
            message = await channel.send(embed=embed)
        except discord.DiscordException as e:
            logger.error("send_alert failed: %s", e)
            return (None, None)

        thread_id = None
        if create_thread:
            try:
                # 스레드 이름 100자 제한 + 빈 이름 방지
                name = (thread_name or "🧵 알람 스레드")[:100]
                thread = await message.create_thread(
                    name=name, auto_archive_duration=1440  # 24h
                )
                thread_id = str(thread.id)
            except discord.DiscordException as e:
                logger.warning("create_thread failed: %s", e)

        return (str(message.id), thread_id)


async def _send_long(channel, text: str) -> None:
    """긴 답변을 Discord 2000자 제한에 맞춰 분할 발송."""
    if not text:
        await channel.send("(응답 없음)")
        return
    for chunk_start in range(0, len(text), 1900):
        await channel.send(text[chunk_start : chunk_start + 1900])
