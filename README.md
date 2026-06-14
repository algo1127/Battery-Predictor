# 🔋 Battery Predictor
*A real-time, ML-enhanced battery analytics engine that actually learns your usage patterns.*

![Version](https://img.shields.io/badge/Version-1.0.1%20(2026--06--12A)-blue)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF)
![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84)
![License](https://img.shields.io/badge/License-MIT-green)

---

## 📖 About
Tired of battery apps that show fake hardcoded numbers, "magic optimization" buttons, and generic estimates? **Battery Predictor** replaces guesswork with real physics and transparent machine learning. 

Built from scratch after reverse-engineering popular "battery optimizer" spyware, this app tracks actual current flow, learns how different app categories drain **your specific device**, and gives you honest, personalized battery life predictions. No root required. No smoke and mirrors.


---

## ✨ Features
- 📊 **Multi-Label Category Profiling**  
  Tracks drain across `Games`, `Video`, `Read`, `Music`, `Phone`, and `Camera` simultaneously. If multiple categories are active during a 1% drop, the drain is logged proportionally to all of them.

- ⚡ **Real Coulomb Counter**  
  Integrates live current flow from Android's `BatteryManager` to calculate true battery capacity vs. factory design capacity.

- 🧠 **Hybrid Prediction Engine**  
  Uses weighted historical averages when data exists. Falls back to real-time `BATTERY_PROPERTY_CURRENT_AVERAGE` physics for instant accuracy on fresh installs.

- 📱 **Smart UI Optimization**  
  - `15s` update interval when screen is ON  
  - `90s` update interval when screen is OFF  
  - Pull-to-refresh & instant foreground sync  
  - Drastically reduces CPU wakeups and battery overhead

- 🛡️ **Android 14+ Compliant**  
  Proper `specialUse` foreground service type, `BOOT_COMPLETED` persistence, and granular permission handling. Won't get killed by modern Doze/APP_STANDBY restrictions.

- 🗃️ **Granular Memory Control**  
  Selectively clear category learning, charging samples, or everything with safe, multi-step confirmation dialogs.

- 🔍 **100% Transparent Math**  
  Every prediction, confidence score, and health metric is calculated from real kernel/hardware data. View the raw JSON memory anytime.

---

## ⚙️ How It Works
1. **Data Collection**  
   A lightweight foreground service monitors battery level, charging state, and app usage via `UsageStatsManager`.

2. **1% Drop Profiling**  
   When the battery drops by 1%, the app calculates which categories were active during that window and logs the time-to-drop to each relevant category.

3. **Weighted Predictions**  
   The `PredictionEngine` fetches your usage distribution (e.g., 60% Read, 40% Games), applies category-specific drain rates, and calculates a personalized time-to-empty/full.

4. **Health Tracking**  
   During charging, the Coulomb counter integrates `BATTERY_PROPERTY_CURRENT_NOW` over time to estimate true capacity. Compared against hardware design capacity for an accurate health percentage.

---

## 📦 Installation
1. Download the latest signed APK from the [Releases](https://github.com/algo1127/Battery-Predictor/releases) page.
2. Enable `Install unknown apps` for your browser/file manager.
3. Install the APK and open the app.
4. Grant **Usage Access** permission when prompted.
5. Tap `Start Tracking` and use your phone normally.
6. Let the app collect data over **1-2 full charge cycles** for optimal predictions.

> 💡 *The app starts with baseline physics predictions and gradually replaces them with learned ML data as you use it.*

---

## 🛠️ Tech Stack
- **Language:** Kotlin
- **UI:** XML Layouts, Custom `BatteryWaveView`, `SwipeRefreshLayout`
- **Core APIs:** `BatteryManager`, `UsageStatsManager`, `SharedPreferences`, `BroadcastReceiver`
- **Architecture:** Foreground Service + Event-driven UI updates
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 36

---


## 🤝 Contributing
Found a bug? Have a feature idea? Open an issue or submit a pull request!  
Please keep contributions focused on:
- Battery accuracy improvements
- UI/UX polish
- Performance optimizations
- Android version compatibility

---

## 📄 License
This project is licensed under the MIT License.

---

## 🙏 Acknowledgments
- Android Open Source Project for `BatteryManager` & `UsageStatsManager` APIs
- The reverse-engineering community for inspiring transparent app development
- Every user who helps the prediction engine learn by using their phone normally!

---
