import { NativeModules, NativeEventEmitter, Platform } from 'react-native';
import type {
  UsbCameraModuleInterface,
  UsbDevice,
  PreviewSize,
  UsbCameraEventType,
  RecordingCompleteEvent,
  AudioRecordingCompleteEvent,
} from './types';

const UNSUPPORTED_ERROR = 'rn-usb-camera is only supported on Android. USB UVC cameras require Android USB Host API.';

const LINKING_ERROR =
  `The package 'rn-usb-camera' doesn't seem to be linked. Make sure:\n\n` +
  Platform.select({ android: '- You have added RnUsbCameraPackage to getPackages() in MainApplication\n' }) +
  '- You rebuilt the app after installing the package';

/** Whether the current platform supports USB cameras */
export const isSupported = Platform.OS === 'android';

function unsupportedPromise<T>(fallback: T): Promise<T> {
  return Promise.reject(new Error(UNSUPPORTED_ERROR));
}

const NativeModule =
  Platform.OS === 'android'
    ? NativeModules.RnUsbCamera ??
      new Proxy(
        {},
        {
          get() {
            throw new Error(LINKING_ERROR);
          },
        }
      )
    : null;

const eventEmitter =
  Platform.OS === 'android' && NativeModule != null
    ? new NativeEventEmitter(NativeModule)
    : null;

export const UsbCamera: UsbCameraModuleInterface & {
  /** Whether the current platform supports USB cameras (Android only) */
  isSupported: boolean;
  addListener(
    event: 'onRecordingComplete',
    callback: (data: RecordingCompleteEvent) => void
  ): { remove(): void };
  addListener(
    event: 'onAudioRecordingComplete',
    callback: (data: AudioRecordingCompleteEvent) => void
  ): { remove(): void };
} = {
  isSupported,
  // ── Device ────────────────────────────────────────────────────────────
  getDeviceList(): Promise<UsbDevice[]> {
    if (!isSupported) return Promise.resolve([]);
    return NativeModule!.getDeviceList();
  },
  requestPermission(deviceId: number): Promise<boolean> {
    if (!isSupported) return unsupportedPromise(false);
    return NativeModule!.requestPermission(deviceId);
  },
  isCameraOpened(): Promise<boolean> {
    if (!isSupported) return Promise.resolve(false);
    return NativeModule!.isCameraOpened();
  },

  // ── Preview ───────────────────────────────────────────────────────────
  getAllPreviewSizes(): Promise<PreviewSize[]> {
    if (!isSupported) return Promise.resolve([]);
    return NativeModule!.getAllPreviewSizes();
  },
  updateResolution(width: number, height: number): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeModule!.updateResolution(width, height);
  },

  // ── Capture ───────────────────────────────────────────────────────────
  captureImage(path?: string | null): Promise<string> {
    if (!isSupported) return unsupportedPromise('');
    return NativeModule!.captureImage(path ?? null);
  },

  // ── Video Recording ───────────────────────────────────────────────────
  startRecording(path?: string | null, durationSec: number = 0): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeModule!.startRecording(path ?? null, durationSec);
  },
  stopRecording(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeModule!.stopRecording();
  },
  isRecording(): Promise<boolean> {
    if (!isSupported) return Promise.resolve(false);
    return NativeModule!.isRecording();
  },

  // ── Audio Recording ───────────────────────────────────────────────────
  startAudioRecording(path?: string | null): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeModule!.startAudioRecording(path ?? null);
  },
  stopAudioRecording(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeModule!.stopAudioRecording();
  },

  // ── Camera Controls ───────────────────────────────────────────────────
  setBrightness(value: number) {
    if (!isSupported) return;
    NativeModule!.setBrightness(value);
  },
  getBrightness(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getBrightness();
  },
  setContrast(value: number) {
    if (!isSupported) return;
    NativeModule!.setContrast(value);
  },
  getContrast(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getContrast();
  },
  setZoom(value: number) {
    if (!isSupported) return;
    NativeModule!.setZoom(value);
  },
  getZoom(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getZoom();
  },
  setSharpness(value: number) {
    if (!isSupported) return;
    NativeModule!.setSharpness(value);
  },
  getSharpness(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getSharpness();
  },
  setSaturation(value: number) {
    if (!isSupported) return;
    NativeModule!.setSaturation(value);
  },
  getSaturation(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getSaturation();
  },
  setHue(value: number) {
    if (!isSupported) return;
    NativeModule!.setHue(value);
  },
  getHue(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getHue();
  },
  setGamma(value: number) {
    if (!isSupported) return;
    NativeModule!.setGamma(value);
  },
  getGamma(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getGamma();
  },
  setGain(value: number) {
    if (!isSupported) return;
    NativeModule!.setGain(value);
  },
  getGain(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeModule!.getGain();
  },
  setAutoFocus(enable: boolean) {
    if (!isSupported) return;
    NativeModule!.setAutoFocus(enable);
  },
  setAutoWhiteBalance(enable: boolean) {
    if (!isSupported) return;
    NativeModule!.setAutoWhiteBalance(enable);
  },
  resetAllControls() {
    if (!isSupported) return;
    NativeModule!.resetAllControls();
  },

  // ── Events ────────────────────────────────────────────────────────────
  addListener(event: UsbCameraEventType, callback: (data: any) => void) {
    if (!isSupported || eventEmitter == null) {
      // Return a no-op subscription on unsupported platforms
      return { remove() {} };
    }
    return eventEmitter.addListener(event, callback);
  },
};
