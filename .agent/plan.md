# Project Plan

Android Notification & Native Web Server (Nginx + Tuna) Control Hub

## Project Brief

# Project Brief: Android Notification & Native Web Server Control Hub

## Features
1. **Notification Forwarding & Extensible Data Engine**: Intercepts Android notifications via `NotificationListenerService` and dispatches formatted JSON payloads to external endpoints using Ktor Client, built on modular `DataCollector` and `DataForwarder` interfaces for future SMS and Call Log expansion.
2. **Native Nginx & Tuna Process Manager**: Deploys and controls native execution of Nginx (with custom `nginx.conf` management) and Tuna binaries to host local web services and expose secure public internet tunnels.
3. **Real-time Console Log Streamer**: Streams live stdout and stderr logs from running Nginx and Tuna background processes into an interactive console view.
4. **Adaptive Multi-Pane Dashboard**: Multi-window / multi-pane layout displaying Process Control/Logs, Notification Logs, and Web Server Configuration simultaneously across phones, foldables, and tablets.

## High-Level Tech Stack
* **Language**: Kotlin
* **UI Framework**: Jetpack Compose
* **Navigation**: Jetpack Navigation 3 (state-driven navigation)
* **Adaptive Strategy**: Compose Material Adaptive library (`androidx.compose.material3.adaptive`)
* **Asynchronous & Concurrency**: Kotlin Coroutines & Flow
* **Networking**: Ktor Client (for JSON payload forwarding)
* **Architecture**: Unidirectional Data Flow (UDF) with ViewModels, utilizing clean interface abstraction (`DataCollector`, `DataForwarder`)
* **Native Execution**: Android Process Execution APIs (`ProcessBuilder`) for managing binary lifecycles and standard stream monitoring

## Implementation Steps
**Total Duration:** 1h 29m 9s

### Task_1_NotificationEngine: Implement NotificationListenerService, DataCollector and DataForwarder interfaces, and Ktor client for forwarding notification JSON payloads.
- **Status:** COMPLETED
- **Updates:** NotificationListenerService, DataCollector/DataForwarder interfaces, and Ktor client successfully created. Notification payloads are properly formatted as JSON and dispatched. DataStore preferences for settings and NotificationLogRepository created. Gradle build assembleDebug and testDebugUnitTest passed successfully.
- **Acceptance Criteria:**
  - NotificationListenerService intercepts notifications
  - DataCollector and DataForwarder interfaces created
  - Ktor client forwards formatted JSON payload to external endpoint
  - build pass
- **Duration:** 16m 52s

### Task_2_ProcessManagerAndLogs: Implement ProcessBuilder execution engine for native Nginx and Tuna binaries, custom nginx.conf management, and real-time stdout/stderr log streaming via Flow.
- **Status:** COMPLETED
- **Updates:** NativeProcessManager, BinaryManager, ProcessLogRepository, NginxConfigManager, NginxProcessController, and TunaProcessController implemented. Real-time stdout/stderr log streaming via Flow verified. Unit tests (53 passed) and assembleDebug verified.
- **Acceptance Criteria:**
  - ProcessBuilder manages Nginx and Tuna binary lifecycles
  - stdout and stderr streamed in real time via Flow
  - Custom nginx.conf configuration management implemented
  - build pass
- **Duration:** 37m 6s

### Task_3_AdaptiveDashboardUI: Build adaptive multi-pane dashboard UI using Jetpack Compose, Material3 Adaptive library, and Navigation 3 with ViewModels for Process Control/Logs, Notification Logs, and Web Server Configuration.
- **Status:** COMPLETED
- **Updates:** Adaptive multi-pane layout implemented using Jetpack Compose, Material 3 Adaptive library, and Navigation 3 patterns. Created MainAdaptiveHub with bottom bar / nav rail switching, Dashboard with process control cards & real-time monospace console viewer, Notification Stream with ListDetailPaneScaffold, Web Server Config with nginx.conf editor and static file manager, and Settings screen with permission banner and package filter rules. All ViewModels implemented and 71 unit tests passed. AssembleDebug passed.
- **Acceptance Criteria:**
  - Navigation 3 state-driven architecture set up
  - Adaptive multi-pane layout supports phone, foldable, and tablet window sizes
  - Interactive console view streams logs and process controls work
  - build pass
- **Duration:** 14m 5s

### Task_4_RunAndVerify: Run and verify the complete application. Instruct critic_agent to verify application stability (no crashes), confirm alignment with user requirements, and report critical UI issues.
- **Status:** COMPLETED
- **Updates:** Completed full QA verification with critic_agent. App installed and executed cleanly. Process manager (Nginx and Tuna) starts natively with SELinux permissions granted from nativeLibraryDir and live stdout/stderr console streaming. Adaptive multi-pane layout verified across compact and expanded screens. 83 unit tests passing and debug assemble passing without crashes.
- **Acceptance Criteria:**
  - build pass
  - make sure all existing tests pass
  - app does not crash
  - critic_agent verified application stability and user requirement alignment
- **Duration:** 21m 6s

