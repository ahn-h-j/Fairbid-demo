# Harness Feedback Log

> 가드레일 실패가 자동으로 기록되는 파일.
> `/evolve` 스킬이 이 파일을 분석하여 하네스 변경을 제안한다.
>
> **상태 설명**
> - `미분석` → 아직 `/evolve`로 분석하지 않은 항목
> - `분석완료` → `/evolve`가 분석했고, 하네스 변경이 적용된 항목
>
> 수동으로 편집하지 마라. hook/pre-commit/gc가 자동으로 append한다.

---

### 2026-04-06 19:30
- **도구**: /gc
- **위반 상세**:
```
transition-all CSS 사용 (31건)
frontend/CLAUDE.md에서 금지하지만 ESLint 규칙이 없었음
```
- **파일**:
  - `AuctionListPage.jsx`
  - `AuctionDetailPage.jsx`
  - `AuctionCreatePage.jsx`
  - `TradeDetailPage.jsx`
  - `LandingPage.jsx`
  - `Pagination.jsx`
  - `ImageUpload.jsx`
  - `ImageGallery.jsx`
- **상태**: 분석완료 — ESLint `no-restricted-syntax` 규칙 추가 + 코드 30건 수정 (2026-04-06)

### 2026-04-06 22:51 ~ 23:48
- **도구**: pre-commit (Checkstyle + ESLint)
- **위반 상세**:
```
하네스 구축 과정에서 발생한 가드레일 실패.
- Checkstyle: 와일드카드 import 26건, 미사용 import 4건
- ESLint: hook 경로 버그로 인한 false positive
```
- **상태**: 분석완료 — 와일드카드 import 전량 수정, hook 버그 수정 (2026-04-06)

### 2026-04-08 16:09
- **도구**: Checkstyle
- **위반 상세**:
```
#
# There is insufficient memory for the Java Runtime Environment to continue.
# Native memory allocation (malloc) failed to allocate 1048576 bytes for AllocateHeap
# An error report file with more information is saved as:
# C:\Users\tkgkd\.gradle\workers\hs_err_pid25636.log
Could not write standard input to Gradle Worker Daemon 4.
java.io.IOException: �������� ������ ���Դϴ�
	at java.base/java.io.FileOutputStream.writeBytes(Native Method)
	at java.base/java.io.FileOutputStream.write(FileOutputStream.java:349)
	at java.base/java.io.BufferedOutputStream.flushBuffer(BufferedOutputStream.java:81)
	at java.base/java.io.BufferedOutputStream.flush(BufferedOutputStream.java:142)
	at org.gradle.process.internal.streams.ExecOutputHandleRunner.writeBuffer(ExecOutputHandleRunner.java:98)
	at org.gradle.process.internal.streams.ExecOutputHandleRunner.forwardContent(ExecOutputHandleRunner.java:85)
	at org.gradle.process.internal.streams.ExecOutputHandleRunner.run(ExecOutputHandleRunner.java:64)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:833)

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':checkstyleMain'.
> A failure occurred while executing org.gradle.api.plugins.quality.internal.CheckstyleAction
   > Failed to run Gradle Worker Daemon
      > Process 'Gradle Worker Daemon 4' finished with non-zero exit value 1

* Try:
> Run with --stacktrace option to get the stack trace.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

BUILD FAILED in 10s
```
- **파일**:
  - `backend/src/main/java/com/cos/fairbid/notification/adapter/out/websocket/WebSocketSessionTracker.java`
- **상태**: 미분석 → `/evolve`로 분석 필요

### 2026-04-11 12:18
- **도구**: Checkstyle
- **위반 상세**:
```
[ant:checkstyle] [ERROR] C:\Users\tkgkd\Desktop\Workspace\FairBid-ai-monitoring\backend\src\main\java\com\cos\fairbid\notification\adapter\out\websocket\WebSocketSessionTracker.java:7:1: [import-grouping] Wrong order for 'org.springframework.context.event.EventListener' import. [ImportOrder]

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':checkstyleMain'.
> A failure occurred while executing org.gradle.api.plugins.quality.internal.CheckstyleAction
   > Checkstyle rule violations were found. See the report at: file:///C:/Users/tkgkd/Desktop/Workspace/FairBid-ai-monitoring/backend/build/reports/checkstyle/main.html
     Checkstyle files with violations: 1
     Checkstyle violations by severity: [error:1]


* Try:
> Run with --scan to get full insights.

BUILD FAILED in 12s
```
- **파일**:
  - `backend/src/main/java/com/cos/fairbid/notification/adapter/out/websocket/WebSocketSessionTracker.java`
- **상태**: 미분석 → `/evolve`로 분석 필요
### 2026-04-12 11:37
- **도구**: ESLint
- **위반 상세**:
```

Oops! Something went wrong! :(

ESLint: 10.2.0

Error [ERR_MODULE_NOT_FOUND]: Cannot find package 'eslint-config-prettier' imported from C:\Users\tkgkd\Desktop\Workspace\FairBid-ai-assist\frontend\eslint.config.js
    at packageResolve (node:internal/modules/esm/resolve:839:9)
    at moduleResolve (node:internal/modules/esm/resolve:908:18)
    at defaultResolve (node:internal/modules/esm/resolve:1039:11)
    at ModuleLoader.defaultResolve (node:internal/modules/esm/loader:554:12)
    at ModuleLoader.resolve (node:internal/modules/esm/loader:523:25)
    at ModuleLoader.getModuleJob (node:internal/modules/esm/loader:246:38)
    at ModuleJob._link (node:internal/modules/esm/module_job:126:49)
```
- **파일**:
  - `frontend/src/api/mutations.js`
  - `frontend/src/pages/AuctionCreatePage.jsx`
- **상태**: 미분석 → `/evolve`로 분석 필요

### 2026-04-18 06:55
- **단계**: Claude Code hook
- **도구**: Bash
- **위반**: sed -i 백업 확장자 누락 (Windows Git Bash에서 파일 손상 위험)
- **명령어**: sed -i 's/foo/bar/' file.txt
- **상태**: open

### 2026-04-18 06:55
- **단계**: Claude Code hook
- **도구**: Bash
- **위반**: sed -i 백업 확장자 누락 (Windows Git Bash에서 파일 손상 위험)
- **명령어**: sed --in-place 's/foo/bar/' file.txt
- **상태**: open
