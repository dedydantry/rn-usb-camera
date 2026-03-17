import { NativeEventEmitter, Platform } from 'react-native';
import type {
  UsbCameraModuleInterface,
  UsbDevice,
  PreviewSize,
  SupportedControls,
  UsbCameraEventType,
  RecordingCompleteEvent,
  AudioRecordingCompleteEvent,
} from './types';
import NativeRnUsbCamera from './NativeRnUsbCamera';

/** Whether the current platform supports USB cameras */
export const isSupported = Platform.OS === 'android';

const unsupportedPromise = <T>(_val: T): Promise<T> =>
  Promise.reject(new Error('USB Camera is only supported on Android'));

const unavailablePromise = <T>(_val: T): Promise<T> =>
  Promise.reject(
    new Error(
      'RnUsbCamera native module is not available. This library requires React Native 0.80+ with the New Architecture enabled and proper autolinking.'
    )
  );

const getNativeModule = () => {
  if (!isSupported) {
    return null;
  }

  return NativeRnUsbCamera ?? null;
};

const nativeModule = getNativeModule();
const eventEmitter = nativeModule ? new NativeEventEmitter(nativeModule) : null;

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
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise([]);
    return nativeModule.getDeviceList();
  },
  requestPermission(deviceId: number): Promise<boolean> {
    if (!isSupported) return unsupportedPromise(false);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(false);
    return nativeModule.requestPermission(deviceId);
  },
  isCameraOpened(): Promise<boolean> {
    if (!isSupported) return Promise.resolve(false);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(false);
    return nativeModule.isCameraOpened();
  },
  openCamera(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.openCamera();
  },
  closeCamera(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.closeCamera();
  },

  // ── Preview ───────────────────────────────────────────────────────────
  getAllPreviewSizes(): Promise<PreviewSize[]> {
    if (!isSupported) return Promise.resolve([]);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise([]);
    return nativeModule.getAllPreviewSizes();
  },
  getCurrentResolution(): Promise<PreviewSize> {
    if (!isSupported) return unsupportedPromise({ width: 0, height: 0 });
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise({ width: 0, height: 0 });
    return nativeModule.getCurrentResolution();
  },
  updateResolution(width: number, height: number): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.updateResolution(width, height);
  },

  // ── Capture ───────────────────────────────────────────────────────────
  captureImage(path?: string | null): Promise<string> {
    if (!isSupported) return unsupportedPromise('');
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise('');
    return nativeModule.captureImage(path ?? null);
  },

  // ── Video Recording ───────────────────────────────────────────────────
  startRecording(path?: string | null, durationSec: number = 0): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.startRecording(path ?? null, durationSec);
  },
  stopRecording(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.stopRecording();
  },
  isRecording(): Promise<boolean> {
    if (!isSupported) return Promise.resolve(false);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(false);
    return nativeModule.isRecording();
  },

  // ── Audio Recording ───────────────────────────────────────────────────
  startAudioRecording(path?: string | null): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.startAudioRecording(path ?? null);
  },
  stopAudioRecording(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(undefined);
    return nativeModule.stopAudioRecording();
  },

  // ── Camera Controls ───────────────────────────────────────────────────
  getSupportedControls(): Promise<SupportedControls> {
    if (!isSupported) return unsupportedPromise({} as SupportedControls);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise({} as SupportedControls);
    return nativeModule.getSupportedControls();
  },
  setBrightness(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setBrightness(value);
  },
  getBrightness(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getBrightness();
  },
  setContrast(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setContrast(value);
  },
  getContrast(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getContrast();
  },
  setZoom(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setZoom(value);
  },
  getZoom(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getZoom();
  },
  setSharpness(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setSharpness(value);
  },
  getSharpness(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getSharpness();
  },
  setSaturation(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setSaturation(value);
  },
  getSaturation(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getSaturation();
  },
  setHue(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setHue(value);
  },
  getHue(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getHue();
  },
  setGamma(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setGamma(value);
  },
  getGamma(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getGamma();
  },
  setGain(value: number) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setGain(value);
  },
  getGain(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    const nativeModule = getNativeModule();
    if (nativeModule == null) return unavailablePromise(0);
    return nativeModule.getGain();
  },
  setAutoFocus(enable: boolean) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setAutoFocus(enable);
  },
  setAutoWhiteBalance(enable: boolean) {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.setAutoWhiteBalance(enable);
  },
  resetAllControls() {
    const nativeModule = getNativeModule();
    if (nativeModule == null) return;
    nativeModule.resetAllControls();
  },

  // ── Events ────────────────────────────────────────────────────────────
  addListener(event: UsbCameraEventType, callback: (data: any) => void) {
    if (!isSupported || eventEmitter == null) {
      return { remove() {} };
    }
    return eventEmitter.addListener(event, callback);
  },
};
