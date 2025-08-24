# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a third-party/community managed controller software for PCPanel devices, written in Java using Spring Boot and JavaFX. The project provides a desktop application for controlling audio, lighting, and other system functions through physical PCPanel hardware devices.

## Development Commands

### Build and Package
- `mvn clean compile` - Compile the project
- `mvn clean package` - Build JAR file and dependencies
- `mvn clean install` - Build platform-specific installers (Windows MSI, Linux DEB)

### Running the Application
- `mvn javafx:run` - Run the application directly via Maven
- For manual JAR execution on Linux, see the extensive JavaFX module path requirements in `linux.md`

### Testing
- `mvn test` - Run all unit tests
- `mvn test -Dtest=ClassName` - Run specific test class

### Development Modes
- Add `hiddebug` argument to enable HID device debugging
- Add `quiet` argument to start without showing main window
- Add `skipfilecheck` argument to skip file validation on startup
- Add `-Ddisable.tray` JVM option to disable system tray (useful for Linux)

## Architecture Overview

### Application Structure
- **Main Entry Points**: `Main.java` (Spring Boot) → `MainFX.java` (JavaFX Application)
- **Spring Boot Integration**: Uses Spring dependency injection with JavaFX UI
- **Platform Support**: Windows (primary), Linux (partial support with limitations)

### Core Components
- **Device Communication** (`hid/`): HID device scanning, input interpretation, output handling
- **Audio Control** (`cpp/`): Platform-specific audio session management
  - Windows: Native DLL via JNA (`SndCtrl.dll`)
  - Linux: PulseAudio integration via `pactl` commands
- **Commands** (`commands/`): Action system for button/dial mappings (volume, media, OBS, VoiceMeeter, etc.)
- **UI Framework** (`ui/`): JavaFX-based configuration interface with FXML layouts
- **Profile Management** (`profile/`): JSON-based settings persistence and device configurations
- **External Integrations**:
  - **OBS** (`obs/`): WebSocket integration for scene/source control
  - **VoiceMeeter** (`voicemeeter/`): Audio mixing software control
  - **MQTT** (`mqtt/`): Home Assistant integration with device discovery
  - **OSC** (`osc/`): Open Sound Control protocol support

### Device Support
- PCPanel Mini, Pro, and RGB variants
- Device-specific UI layouts in `assets/PCPanelMini/`, `assets/PCPanelPro/`, `assets/PCPanelRGB/`
- Lighting control with per-device configuration options

### Platform Differences
- **Windows**: Full feature support including window focus detection, native audio control
- **Linux**: Limited support - no window focus on Wayland, requires PulseAudio, manual device permissions setup

### Key Design Patterns
- Spring Boot services for business logic (`@Service` annotations throughout)
- JavaFX FXML for UI definition with corresponding controller classes
- Event-driven architecture for device communication and audio events
- Strategy pattern for platform-specific implementations (Windows vs Linux)

### Configuration
- Settings stored in `~/.pcpanel/profiles.json`
- Application properties support Maven filtering for version injection
- Lombok used extensively for reducing boilerplate code

### Build System
- Maven with custom JavaFX and jpackage integration
- Platform-specific packaging profiles (Windows MSI with custom launch script, Linux DEB)
- Native module requirements due to JavaFX and JNA dependencies