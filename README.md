# rn-usb-camera

React Native library for **Android USB (UVC) cameras**, powered by [AUSBC](https://github.com/ernestp/AndroidUSBCamera).

Open any USB UVC camera on Android without root — no system camera permissions required. Just plug in a USB camera via OTG and start streaming.

> **Platform support:** Android only. On iOS, the library provides graceful fallbacks (placeholder UI + safe no-op API) so your cross-platform app won't crash.

## Features

- 📷 Live USB camera preview via native `TextureView`
- 📸 Capture JPEG photos
- 🎥 Record MP4 video (with optional auto-split duration)
- 🎙️ Record audio (MP3) from system mic or camera UAC mic
- 🔧 Full UVC camera controls (brightness, contrast, zoom, sharpness, saturation, hue, gamma, gain, auto-focus, auto-white-balance)
- 📐 Query & change preview resolutions at runtime
- 🔌 USB device attach/detach events
- 🍎 iOS safe — renders a placeholder view, API methods return safe defaults
- 🏗️ TypeScript-first with full type definitions

## Requirements

| | Minimum |
|---|---|
| React Native | 0.73+ |
| Android SDK | API 23 (Android 6.0) |
| Device | USB OTG support required |

## Installation

```bash
npm install rn-usb-camera
# or
yarn add rn-usb-camera
```

### Android Setup

#### 1. Register the package

**React Native 0.73+ with autolinking** — no manual linking needed if your app uses the standard autolinking setup.

If you're **not** using autolinking, add the package manually in your `MainApplication.java` or `MainApplication.kt`:

```java
import com.rnusbcamera.RnUsbCameraPackage;

@Override
protected List<ReactPackage> getPackages() {
    List<ReactPackage> packages = new PackageList(this).getPackages();
    packages.add(new RnUsbCameraPackage());
    return packages;
}
```

#### 2. USB host feature (optional)

The library's `AndroidManifest.xml` already declares `android.hardware.usb.host` (with `required="false"`). If your app **requires** a USB host, you can add this to your app's `AndroidManifest.xml`:

```xml
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```

#### 3. Rebuild

```bash
npx react-native run-android
```

## Quick Start

```tsx
import React from 'react';
import { View, Button, Alert } from 'react-native';
import { UsbCameraView, UsbCamera, isSupported } from 'rn-usb-camera';

export default function App() {
  const handleCapture = async () => {
    try {
      const path = await UsbCamera.captureImage();
      Alert.alert('Photo saved', path);
    } catch (e) {
      Alert.alert('Error', e.message);
    }
  };

  return (
    <View style={{ flex: 1 }}>
      <UsbCameraView
        style={{ flex: 1 }}
        previewWidth={1280}
        previewHeight={720}
        autoOpen={true}
        onCameraOpened={() => console.log('Camera opened')}
        onError={(e) => console.warn('Camera error:', e.nativeEvent.message)}
      />
      <Button title="Take Photo" onPress={handleCapture} />
    </View>
  );
}
```

## API Reference

### `<UsbCameraView />`

Native camera preview component.

#### Props

| Prop | Type | Default | Description |
|---|---|---|---|
| `style` | `ViewStyle` | — | View style |
| `previewWidth` | `number` | `640` | Camera preview width in pixels |
| `previewHeight` | `number` | `480` | Camera preview height in pixels |
| `autoOpen` | `boolean` | `true` | Automatically open camera when USB device is attached |
| `onDeviceAttached` | `(event) => void` | — | Called when a USB camera is plugged in. `event.nativeEvent` contains `{ deviceId, vendorId, productId, deviceName }` |
| `onDeviceDetached` | `(event) => void` | — | Called when a USB camera is unplugged. `event.nativeEvent` contains `{ deviceId }` |
| `onCameraOpened` | `(event) => void` | — | Called when camera preview starts |
| `onCameraClosed` | `(event) => void` | — | Called when camera preview stops |
| `onError` | `(event) => void` | — | Called on error. `event.nativeEvent` contains `{ message }` |
| `children` | `ReactNode` | — | Custom fallback content for unsupported platforms (iOS) |

**iOS behavior:** Renders a dark placeholder with a "USB Camera not available" message. Pass `children` to customize:

```tsx
<UsbCameraView style={{ flex: 1 }}>
  <Text>USB cameras are not supported on iOS.</Text>
</UsbCameraView>
```

---

### `UsbCamera`

Imperative API for camera operations.

#### Platform Check

```ts
import { UsbCamera, isSupported } from 'rn-usb-camera';

// Module-level export
if (isSupported) { /* Android */ }

// Or on the object
if (UsbCamera.isSupported) { /* Android */ }
```

#### Device Management

```ts
// Get connected USB camera devices
const devices: UsbDevice[] = await UsbCamera.getDeviceList();
// => [{ deviceId: 1, vendorId: 0x1234, productId: 0x5678, deviceName: '...' }]

// Request USB permission for a specific device
const granted: boolean = await UsbCamera.requestPermission(deviceId);

// Check if camera is currently open
const opened: boolean = await UsbCamera.isCameraOpened();
```

#### Preview Sizes

```ts
// Get all supported preview resolutions
const sizes: PreviewSize[] = await UsbCamera.getAllPreviewSizes();
// => [{ width: 640, height: 480 }, { width: 1280, height: 720 }, ...]

// Change resolution at runtime (will briefly close and reopen the camera)
await UsbCamera.updateResolution(1920, 1080);
```

#### Photo Capture

```ts
// Capture JPEG photo (saved to default DCIM/Camera directory)
const filePath: string = await UsbCamera.captureImage();

// Capture to a specific path
const filePath: string = await UsbCamera.captureImage('/sdcard/DCIM/my_photo.jpg');
```

#### Video Recording

```ts
// Start recording (saved to default directory)
await UsbCamera.startRecording();

// Start recording with custom path and auto-split every 60 seconds
await UsbCamera.startRecording('/sdcard/DCIM/video.mp4', 60);

// Stop recording
await UsbCamera.stopRecording();

// Check recording state
const recording: boolean = await UsbCamera.isRecording();

// Listen for recording completion (e.g., auto-split segments)
const subscription = UsbCamera.addListener('onRecordingComplete', (event) => {
  console.log('Video saved to:', event.path);
});

// Clean up
subscription.remove();
```

#### Audio Recording

```ts
// Start MP3 audio recording
await UsbCamera.startAudioRecording();

// With custom path
await UsbCamera.startAudioRecording('/sdcard/DCIM/audio.mp3');

// Stop
await UsbCamera.stopAudioRecording();

// Listen for completion
const subscription = UsbCamera.addListener('onAudioRecordingComplete', (event) => {
  console.log('Audio saved to:', event.path);
});
```

#### Camera Controls

All setter methods are synchronous fire-and-forget. Getter methods return promises.

```ts
// Brightness
UsbCamera.setBrightness(100);
const brightness: number = await UsbCamera.getBrightness();

// Contrast
UsbCamera.setContrast(50);
const contrast: number = await UsbCamera.getContrast();

// Zoom
UsbCamera.setZoom(2);
const zoom: number = await UsbCamera.getZoom();

// Sharpness
UsbCamera.setSharpness(30);
const sharpness: number = await UsbCamera.getSharpness();

// Saturation
UsbCamera.setSaturation(60);
const saturation: number = await UsbCamera.getSaturation();

// Hue
UsbCamera.setHue(10);
const hue: number = await UsbCamera.getHue();

// Gamma
UsbCamera.setGamma(5);
const gamma: number = await UsbCamera.getGamma();

// Gain
UsbCamera.setGain(20);
const gain: number = await UsbCamera.getGain();

// Auto-focus
UsbCamera.setAutoFocus(true);

// Auto white balance
UsbCamera.setAutoWhiteBalance(true);

// Reset all controls to defaults
UsbCamera.resetAllControls();
```

## iOS Behavior

This library is **Android-only** by design (USB UVC cameras require the Android USB Host API). However, it's fully safe to include in cross-platform projects:

| Feature | iOS Behavior |
|---|---|
| `<UsbCameraView />` | Renders a styled placeholder ("USB Camera not available") or your custom `children` |
| `isSupported` | Returns `false` |
| `getDeviceList()` | Resolves with `[]` |
| `isCameraOpened()` | Resolves with `false` |
| `isRecording()` | Resolves with `false` |
| `getBrightness()` etc. | Resolves with `0` |
| `captureImage()` | Rejects with descriptive error |
| `startRecording()` | Rejects with descriptive error |
| `setBrightness()` etc. | Silent no-op |
| `addListener()` | Returns no-op `{ remove() {} }` |

This lets you write platform-conditional logic cleanly:

```tsx
import { UsbCamera, isSupported, UsbCameraView } from 'rn-usb-camera';

function CameraScreen() {
  if (!isSupported) {
    return <Text>USB Camera requires an Android device with USB OTG.</Text>;
  }
  return <UsbCameraView style={{ flex: 1 }} />;
}
```

## TypeScript

All types are exported:

```ts
import type {
  UsbDevice,
  PreviewSize,
  UsbCameraViewProps,
  DeviceAttachedEvent,
  DeviceDetachedEvent,
  CameraOpenedEvent,
  CameraClosedEvent,
  CameraErrorEvent,
  RecordingCompleteEvent,
  AudioRecordingCompleteEvent,
  UsbCameraModuleInterface,
} from 'rn-usb-camera';
```

## Troubleshooting

### Camera not detected

- Ensure your Android device supports **USB OTG**
- Try a different USB OTG adapter/cable
- Check that the camera is a **UVC-compliant** USB camera
- Some devices need USB debugging disabled for OTG to work

### Permission dialog not showing

- The USB permission dialog is system-managed — make sure you're not blocking it
- Call `UsbCamera.requestPermission(deviceId)` manually if `autoOpen` doesn't trigger it

### Preview is black

- Try lowering the resolution: `previewWidth={640} previewHeight={480}`
- Switch preview format — some cameras only support YUYV, not MJPEG
- Check `onError` callback for specific error messages

### Build errors

- Make sure you're using React Native 0.73+ and compileSdkVersion 34+
- Run `cd android && ./gradlew clean` and rebuild

## Credits

Powered by [AUSBC (Android USB Camera)](https://github.com/ernestp/AndroidUSBCamera) — the actively maintained fork of AndroidUSBCamera.

## License

Apache-2.0
