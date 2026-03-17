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

const unsupportedPromise = <T>(val: T): Promise<T> =>
  Promise.reject(new Error('USB Camera is only supported on Android'));

const eventEmitter = isSupported ? new NativeEventEmitter(NativeRnUsbCamera) : null;

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
    return NativeRnUsbCamera.getDeviceList();
  },
  requestPermission(deviceId: number): Promise<boolean> {
    if (!isSupported) return unsupportedPromise(false);
    return NativeRnUsbCamera.requestPermission(deviceId);
  },
  isCameraOpened(): Promise<boolean> {
    if (!isSupported) return Promise.resolve(false);
    return NativeRnUsbCamera.isCameraOpened();
  },
  openCamera(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.openCamera();
  },
  closeCamera(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.closeCamera();
  },

  // ── Preview ───────────────────────────────────────────────────────────
  getAllPreviewSizes(): Promise<PreviewSize[]> {
    if (!isSupported) return Promise.resolve([]);
    return NativeRnUsbCamera.getAllPreviewSizes();
  },
  getCurrentResolution(): Promise<PreviewSize> {
    if (!isSupported) return unsupportedPromise({ width: 0, height: 0 });
    return NativeRnUsbCamera.getCurrentResolution();
  },
  updateResolution(width: number, height: number): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.updateResolution(width, height);
  },

  // ── Capture ───────────────────────────────────────────────────────────
  captureImage(path?: string | null): Promise<string> {
    if (!isSupported) return unsupportedPromise('');
    return NativeRnUsbCamera.captureImage(path ?? null);
  },

  // ── Video Recording ───────────────────────────────────────────────────
  startRecording(path?: string | null, durationSec: number = 0): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.startRecording(path ?? null, durationSec);
  },
  stopRecording(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.stopRecording();
  },
  isRecording(): Promise<boolean> {
    if (!isSupported) return Promise.resolve(false);
    return NativeRnUsbCamera.isRecording();
  },

  // ── Audio Recording ───────────────────────────────────────────────────
  startAudioRecording(path?: string | null): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.startAudioRecording(path ?? null);
  },
  stopAudioRecording(): Promise<void> {
    if (!isSupported) return unsupportedPromise(undefined);
    return NativeRnUsbCamera.stopAudioRecording();
  },

  // ── Camera Controls ───────────────────────────────────────────────────
  getSupportedControls(): Promise<SupportedControls> {
    if (!isSupported) return unsupportedPromise({} as SupportedControls);
    return NativeRnUsbCamera.getSupportedControls();
  },
  setBrightness(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setBrightness(value);
  },
  getBrightness(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getBrightness();
  },
  setContrast(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setContrast(value);
  },
  getContrast(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getContrast();
  },
  setZoom(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setZoom(value);
  },
  getZoom(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getZoom();
  },
  setSharpness(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setSharpness(value);
  },
  getSharpness(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getSharpness();
  },
  setSaturation(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setSaturation(value);
  },
  getSaturation(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getSaturation();
  },
  setHue(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setHue(value);
  },
  getHue(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getHue();
  },
  setGamma(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setGamma(value);
  },
  getGamma(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getGamma();
  },
  setGain(value: number) {
    if (!isSupported) return;
    NativeRnUsbCamera.setGain(value);
  },
  getGain(): Promise<number> {
    if (!isSupported) return Promise.resolve(0);
    return NativeRnUsbCamera.getGain();
  },
  setAutoFocus(enable: boolean) {
    if (!isSupported) return;
    NativeRnUsbCamera.setAutoFocus(enable);
  },
  setAutoWhiteBalance(enable: boolean) {
    if (!isSupported) return;
    NativeRnUsbCamera.setAutoWhiteBalance(enable);
  },
  resetAllControls() {
    if (!isSupported) return;
    NativeRnUsbCamera.resetAllControls();
  },

  // ── Events ────────────────────────────────────────────────────────────
  addListener(event: UsbCameraEventType, callback: (data: any) => void) {
    if (!isSupported || eventEmitter == null) {
      return { remove() {} };
    }
    return eventEmitter.addListener(event, callback);
  },
};
