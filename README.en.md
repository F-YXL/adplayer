# adplayer

Turn a cheap RK3128 Android ad machine (Android 4.4.4 / API 19) into a video + image loop player. Everything is operable from an IR remote; media is uploaded from a phone browser over Wi-Fi.

- No Gradle, no AndroidX: aapt + javac + d8, APK under 100 KB
- Layouts: single / left-right / top-bottom / 2x2 grid; video + image mixed playback
- Remote-first settings (arrows + OK + numeric keys, no soft keyboard)
- Scheduled power on (RTC wake) / power off / reboot / screen off
- Built-in HTTP server on port 8080 for upload, thumbnails, batch delete, auto-categorise and auto-transcode to H.264

## Requirements

- Android 4.4.4 (API 19), rooted device
- JDK 8 + Android SDK (platforms;android-19, build-tools 34.x) to build from source

## Download

Grab `adplayer-<version>.apk` from [Releases](https://github.com/F-YXL/adplayer/releases). Note: since 1.2.0 the package id is `io.github.fyxl.adplayer`, so upgrading from 1.1.0 requires uninstalling the old app first.

## Build

    export ANDROID_TOOLCHAIN=/path/to/toolchain   # contains jdk/ and sdk/
    export KEYSTORE=/path/to/your.keystore
    export KEYSTORE_ALIAS=your_alias
    export KEYSTORE_PASS=your_password
    export KEY_PASS=your_password
    ./scripts/build.sh

## Install

    export DEVICE_IP=192.0.2.10   # your device LAN address
    ./scripts/install.sh

Then open `http://<device-ip>:8080` on a phone in the same network.

## Security

The HTTP media server has **no authentication** and the app requires root. Run it only on a trusted LAN, never expose port 8080 to the internet. See [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE).

