<div align="center">

# StreamGo DJI by Swissi 🇨🇭

### Android-App zur Steuerung von DJI Action- und Pocket-Kameras über Bluetooth & Wi-Fi für RTMP-Livestreaming.

[![GitHub Release](https://img.shields.io/github/v/release/powerswissi/StreamGo-DJI?style=for-the-badge&color=brightgreen)](https://github.com/powerswissi/StreamGo-DJI/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/powerswissi/StreamGo-DJI/total?style=for-the-badge&color=blue)](https://github.com/powerswissi/StreamGo-DJI/releases)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-green?style=for-the-badge&logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-orange?style=for-the-badge)](LICENSE)

<br/>

[📥 **Download Neueste APK**](https://github.com/powerswissi/StreamGo-DJI/releases/latest) • [Release Notes](https://github.com/powerswissi/StreamGo-DJI/releases) • [Problem melden](https://github.com/powerswissi/StreamGo-DJI/issues)

</div>

<br/>

> [!NOTE]
> **✨ 100% Vibe Coding Projekt:**  
> Entwickelt mit Leidenschaft und KI-Unterstützung für den harten IRL-Einsatz in der Praxis. Kein Ballast, kein Schnickschnack – sondern maximale Ausfallsicherheit beim Streamen unterwegs! 🚀

---

## 📸 Screenshots

| 🎛️ Profile & Status | 📡 Kamera & WLAN | ⚙️ Stream-Setup | 📱 Homescreen Widget |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/profiles.png" width="220" alt="Profile Screen"> | <img src="docs/screenshots/camera-wifi.png" width="220" alt="Camera & WiFi"> | <img src="docs/screenshots/stream-settings.png" width="220" alt="Stream Settings"> | <img src="docs/screenshots/widget.png" width="220" alt="Android Widget"> |

---

## ✨ Features

- 📱 **Eigener Smartphone-Hotspot:** Direkte Schnell-Auswahl für den lokalen Handy-Hotspot, auch wenn Android diesen nicht in den WLAN-Scan aufnimmt.
- ⚡ **1-Click Stream direkt aus der Profil-Liste:** Start / Stop ohne Umwege.
- 📱 **Android Homescreen Widget:** Stream direkt vom Smartphone-Homescreen starten & stoppen inkl. Live-Status und Akkuanzeige.
- 🎛️ **Eigenständiges Preset-System:** 
  - Beliebig viele Streaming- & WLAN-Vorlagen erstellen, editieren und verwalten.
  - Sichere Vorlagen: Das in der Kamera konfigurierte Netzwerk wird niemals unbeabsichtigt überschrieben.
- 📷 **QR-Code Import & Export:**
  - **Live-Scan:** Streaming-Vorlagen via Google Code Scanner sofort per Kamera einlesen.
  - **Galerie-Scan:** QR-Codes direkt aus gespeicherten Screenshots/Bildern decodieren.
  - **Offline-Generator:** Eigene Vorlagen als QR-Code erzeugen und teilen.
  - **Import aus Kamera-Feed:** RTMP- & WLAN-Daten blitzschnell aus dem aktiven Profil übernehmen.
- 🔍 **Integrierte WLAN-Suche & Scanner:** Listet Netzwerke in Reichweite mit Signalstärke (dBm), GHz-Band (2.4/5 GHz) & Verschlüsselung auf.
- 👁️ **WLAN-Passwort-Sichtbarkeit:** Passwort bei der Eingabe jederzeit per Augensymbol ein- und ausblenden.
- 🏷️ **Eigene Kamera-Nicknames:** z.B. *Helm-Cam*, *Bike-Front*, *Rucksack-Cam* zur schnellen Unterscheidung.
- ⚡ **1-Click Belabox Setup:** Automatisches Ausfüllen der RTMP-URL für Belabox-Hotspots.
- 📊 **Echtzeit-Telemetrie:**
  - Live-Encoder-Bitrate in Mbit/s
  - Stream-Stoppuhr & geschätzter Datenverbrauch (MB / GB)
  - Live-Akkustand (%) & Ladezustand
  - WLAN-Signal & Frequenzband
- 🔄 **Automatischer Reconnect:** Stellt den Stream bei Signalabriss automatisch wieder her.
- 🛑 **Smarter Stop-Filter:** Erkennt gewollte Abbrüche am Kamera-Touchscreen und verhindert ungewollte Schleifen.

---

## 🎥 Unterstützte Kameras

| Modell | Status | Anmerkung |
| :--- | :---: | :--- |
| **DJI Osmo Action 4** | ✅ Getestet | Vollständig im Live-Einsatz erprobt und optimiert. |
| **DJI Osmo Action 5 Pro** | 🧪 Experimentell | Protokollunterstützung integriert (Feedback willkommen). |
| **DJI Osmo Action 3 / 6** | 🧪 Experimentell | Basisprotokoll kompatibel. |
| **DJI Osmo Pocket 3** | 🧪 Experimentell | Protokollunterstützung integriert. |

---

## 🚀 Schnellstart & Installation

1. Gehe zu den [**Releases**](https://github.com/powerswissi/StreamGo-DJI/releases/latest).
2. Lade die Datei `dji-remote-v1.0.0-release.apk` auf dein Android-Smartphone herunter.
3. Öffne die APK und erlaube die Installation aus unbekannten Quellen.
4. Schalte Bluetooth & Standort auf deinem Handy ein, schalte deine DJI-Kamera ein und verbinde dich in der App!

---

## 🛠️ Für Entwickler & Selbstbauer

```bash
# Repository klonen
git clone https://github.com/powerswissi/StreamGo-DJI.git
cd StreamGo-DJI/dji-remote-main

# Debug-APK kompilieren
./gradlew assembleDebug

# Release-APK erstellen
./gradlew assembleRelease
```

---

## 🤝 Danksagung & Credits

Dieses Projekt basiert auf den herausragenden Vorarbeiten der Community:
- [dimadesu/dji-remote](https://github.com/dimadesu/dji-remote)
- [Moblin](https://github.com/eerimoq/Moblin)
- [Spillmaker](https://github.com/Spillmaker) (DJI BLE Reverse Engineering)

## 📄 Lizenz

Dieses Projekt steht unter der [MIT License](LICENSE).  
Ursprünglicher Code Copyright (c) 2026 Dmytro Antonov • Erweiterungen & Anpassungen Copyright (c) 2026 Swissi.
