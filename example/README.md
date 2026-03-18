# Example App

This folder contains an Expo bare example app for testing `rn-usb-camera` with:

- Expo SDK 54
- React Native 0.81
- New Architecture enabled

## What it does

The example app shows how to:

- render `UsbCameraView`
- list connected USB cameras
- capture an image
- start and stop video recording
- change preview resolution
- listen to recording completion events

## Run the example

From the repository root:

```bash
cd example
npm install
npx expo install --fix
npx expo run:android
```

If you want to start Metro for the development build:

```bash
cd example
npx expo start --dev-client
```

## Important notes

- Use a physical Android device with USB OTG support.
- Plug in a UVC USB camera before testing camera actions.
- This library is Android only.
- The example already enables the New Architecture in `android/gradle.properties`.
- Use Node.js 20.19.4 or newer because React Native 0.81 enforces that minimum engine.
- If `../../AndroidUSBCamera` exists, the Android example includes it as a Gradle composite build and substitutes `com.github.ernestp:libausbc`, `libuvc`, and `libnative` directly from source. This is the path used to make `npx expo run:android` reliable under Expo's configure-on-demand Gradle invocation.
- If you change the native AUSBC source, rerun `npm run sync:ausbc` from the repository root to refresh the local Maven repo and bundled fallback AARs.

## How the local library is linked

The example depends on the library through:

```json
"rn-usb-camera": "file:.."
```

That means changes in the root package can be reinstalled into the example by running:

```bash
cd example
npm install
```