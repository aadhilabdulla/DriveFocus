# DriveFocus 📵

**DriveFocus** is a modern Android application designed to enhance driver safety by automatically managing incoming phone calls when the user is detected to be driving. The app ensures that drivers are not distracted by calls while still providing a configurable emergency override for urgent situations.

> **Note:** DriveFocus serves as a real-world reference project for implementing **persistent foreground services**, **Inter-Process Communication (IPC)**, **sensitive runtime permissions**, and **modern Android system integrations**.

---

## 🧩 Tech Stack

| Category | Technology |
| :--- |:-----|
| **Language** | 100% Kotlin |
| **UI** | Jetpack Compose |
| **Dependency Injection** | Hilt |
| **Location** | FusedLocationProviderClient |
| **Background Processing** | Foreground & System-started Services |
| **Permissions** | RoleManager API |

---

## ✨ Features

### 🚘 Automatic Driving Detection
* **Real-time Monitoring:** A persistent foreground service monitors real-time speed.
* **Smart Debouncing:** Uses a debouncing algorithm to avoid false positives caused by GPS drift.
* **Threshold Activation:** Driving mode activates only after consecutive high-speed readings.

### 📵 Automated Call Rejection
* Incoming calls are intercepted and rejected before the phone rings while driving mode is active.

### 💬 Custom SMS Response
* Automatically sends the following SMS to the caller:
  > "Sorry I am driving. Call me again if it's an emergency."

### 🚨 Emergency Callback Override
* If the same number calls back within **1 minute**, the call is allowed to ring through.
* The timer resets for that specific number after the override.

### 🎨 Modern, Reactive UI
* Built entirely using **Jetpack Compose**.
* Features a single animated toggle button that changes border color and size based on state.
* UI state remains perfectly synchronized with the background service.

### 🔐 System-Integrated Permissions
* Uses the **RoleManager API** to request the “Caller ID & spam app” role.
* This is strictly required for call screening on modern Android versions.

---

## 🏗️ Technical Architecture

DriveFocus solves a complex Android problem: **Sharing state between two services running in different processes.** The architecture is designed to be robust and respect modern Android's strict background execution rules.

### 1️⃣ DriveFocusService (Foreground Service)
**Responsibility:** Detect whether the user is driving.

* **Implementation:**
    * Runs as a foreground service with a persistent notification.
    * Uses `FusedLocationProviderClient` for speed updates.
* **State Management (Writer):**
    * Acts as the state **writer**.
    * Stores `is_service_running` and `is_driving` in `SharedPreferences`.
    * *Note: SharedPreferences is used here as a critical tool for cross-process state sharing.*

### 2️⃣ CallScreeningService (Background Call Management)
**Responsibility:** Intercept and manage incoming phone calls.

* **Implementation:**
    * Registered in `AndroidManifest.xml` with the `android.permission.BIND_SCREENING_SERVICE` permission.
    * **Process:** Started by the Android Telecom system (not the app), often in a separate, isolated process.
* **Logic (Reader):**
    * Acts as the state **reader**.
    * Reads `is_service_running` and `is_driving` from `SharedPreferences`.
    * If conditions are met, it:
        1. Rejects the call.
        2. Sends the SMS directly.
        3. Records the call history to `SharedPreferences`.

### 3️⃣ HomeViewModel & Jetpack Compose UI
**Responsibility:** Manage and display the UI, ensuring synchronization with the background service.

* **Implementation:**
    * Registers an `OnSharedPreferenceChangeListener` to listen for changes to the `SharedPreferences` file.
    * Updates a `StateFlow` when changes occur.
    * The Jetpack Compose UI observes the `StateFlow` and automatically recomposes.

---

## ⚠️ Important Note on Device Compatibility

The core call-blocking feature relies on the `CallScreeningService` being started by the Android system in the background. Extensive debugging has revealed a critical distinction in how different Android OS versions handle this.

### ✅ Works Perfectly On:
* Google Pixel
* Motorola
* Android Emulators
* Devices running "stock" or near-stock Android.

*These devices correctly follow the official Android documentation, allowing a registered `CallScreeningService` to start when a call comes in.*

### ❌ FAILS On:
* Samsung
* Xiaomi (MIUI)
* OnePlus (OxygenOS)
* Huawei
* Most devices with heavily customized Android versions.

### Reason for Failure
These manufacturers implement aggressive, non-standard security policies. Even when a user grants all necessary permissions (including setting the app as the default Caller ID app and disabling battery optimizations), these operating systems actively and silently block the `CallScreeningService` from starting when the app is in the background.

> The system does not throw an error or crash the app; it simply ignores the request to start the service. This is a deliberate design choice by OEMs to conserve battery life, affecting all but a few "whitelisted" major applications (e.g., WhatsApp, Facebook).