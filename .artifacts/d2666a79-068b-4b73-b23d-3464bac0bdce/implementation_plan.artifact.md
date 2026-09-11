# Implementation Plan - XREFF Project Initialization

This plan outlines the steps to initialize the XREFF project with the requested theme, navigation, and WebSocket core.

## User Review Required

> [!IMPORTANT]
> The package name will be changed from `id.xterm.xreff` to `id.xterm.xref` as requested.
> WebSocket implementation will be adapted from the reference project `D:\android\xterm-mig33-kotlin`.

## Proposed Changes

### Project Configuration

#### [MODIFY] [build.gradle.kts](file:///D:/android/xreff/app/build.gradle.kts)
- Update `namespace` and `applicationId` to `id.xterm.xref`.

#### [MODIFY] [AndroidManifest.xml](file:///D:/android/xreff/app/src/main/AndroidManifest.xml)
- Update package references if any.

### Theme Implementation

#### [NEW] [Color.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/ui/theme/Color.kt)
- Define `Greenlight` colors: Background `#0A0F0A`, Neon Green `#00FF41`.

#### [NEW] [Type.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/ui/theme/Type.kt)
- Define monospace/terminal-like typography.

#### [NEW] [Theme.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/ui/theme/Theme.kt)
- Implement `GreenlightTheme` with dynamic color support and fallbacks.

### Navigation Skeleton

#### [NEW] [Destinations.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/ui/navigation/Destinations.kt)
- Define `NavKey` serializable objects for Bagan, Statistics, Room, and Dashboard.

#### [NEW] [MainScreen.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/ui/MainScreen.kt)
- Implement Bottom Navigation using Navigation 3.

### WebSocket Core

#### [NEW] [WebSocketClient.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/core/websocket/WebSocketClient.kt)
- Port the wrapper from the reference project.

#### [NEW] [WebSocketModels.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/core/websocket/WebSocketModels.kt)
- Define data classes for `auth.required`, `developer.login`, `session.ready`, `ping`, and `room.join`.

#### [NEW] [WebSocketRepository.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/data/repository/WebSocketRepository.kt)
- Manage connection state and message handling.

#### [NEW] [WebSocketService.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/core/service/WebSocketService.kt)
- Foreground service to maintain WebSocket connection.

### Entry Point

#### [MODIFY] [MainActivity.kt](file:///D:/android/xreff/app/src/main/java/id/xterm/xref/MainActivity.kt)
- Set up the `GreenlightTheme` and `NavDisplay`.

## Verification Plan

### Automated Tests
- Build the project using `./gradlew assembleDebug`.

### Manual Verification
- Verify the UI layout and theme in Android Studio Preview.
- Check logs for WebSocket connection attempts (if testable).
