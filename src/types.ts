import type React from 'react';
import type { ViewStyle } from 'react-native';

// ── Device ───────────────────────────────────────────────────────────────

export interface UsbDevice {
  deviceId: number;
  vendorId: number;
  productId: number;
  deviceName: string;
}

// ── Preview Size ─────────────────────────────────────────────────────────

export interface PreviewSize {
  width: number;
  height: number;
}

export type PreviewRotation =
  | '0'
  | '90'
  | '180'
  | '270'
  | 'flipVertical'
  | 'flipHorizontal';

export type PreviewResizeMode = 'cover' | 'contain';

// ── Events ───────────────────────────────────────────────────────────────

export interface DeviceAttachedEvent {
  nativeEvent: UsbDevice;
}

export interface DeviceDetachedEvent {
  nativeEvent: { deviceId: number };
}

export interface CameraOpenedEvent {
  nativeEvent: {};
}

export interface CameraClosedEvent {
  nativeEvent: {};
}

export interface CameraLoadingEvent {
  nativeEvent: {};
}

export interface CameraErrorEvent {
  nativeEvent: { message: string };
}

export interface RecordingCompleteEvent {
  path: string;
}

export interface AudioRecordingCompleteEvent {
  path: string;
}

// ── Camera Controls ──────────────────────────────────────────────────────

export interface CameraControlInfo {
  supported: boolean;
  min: number;
  max: number;
  current: number;
}

export interface SupportedControls {
  brightness: CameraControlInfo;
  contrast: CameraControlInfo;
  sharpness: CameraControlInfo;
  gain: CameraControlInfo;
  gamma: CameraControlInfo;
  saturation: CameraControlInfo;
  hue: CameraControlInfo;
  zoom: CameraControlInfo;
}

// ── View Props ───────────────────────────────────────────────────────────

export interface UsbCameraViewProps {
  style?: ViewStyle;
  /** Preview width in pixels (default: 640) */
  previewWidth?: number;
  /** Preview height in pixels (default: 480) */
  previewHeight?: number;
  /** Preview rotation or flip applied by the native renderer (default: '0') */
  previewRotation?: PreviewRotation;
  /** Mirrors the live preview horizontally (default: false) */
  liveViewMirror?: boolean;
  /** Mirrors the captured image horizontally before returning the file path (default: false) */
  captureMirror?: boolean;
  /** How the native preview should fit inside the view bounds (default: 'cover') */
  resizeMode?: PreviewResizeMode;
  /** Auto-open camera when USB device is attached (default: true) */
  autoOpen?: boolean;
  /** Called when a USB camera device is attached */
  onDeviceAttached?: (event: DeviceAttachedEvent) => void;
  /** Called when a USB camera device is detached */
  onDeviceDetached?: (event: DeviceDetachedEvent) => void;
  /** Called when camera preview starts */
  onCameraOpened?: (event: CameraOpenedEvent) => void;
  /** Called when camera preview stops */
  onCameraClosed?: (event: CameraClosedEvent) => void;
  /** Called when camera is loading (e.g. resolution change) */
  onCameraLoading?: (event: CameraLoadingEvent) => void;
  /** Called on camera error */
  onError?: (event: CameraErrorEvent) => void;
  /** Children to render on unsupported platforms (iOS). If not provided, a default message is shown. */
  children?: React.ReactNode;
}

// ── Module API ───────────────────────────────────────────────────────────

export interface UsbCameraModuleInterface {
  // Device
  getDeviceList(): Promise<UsbDevice[]>;
  requestPermission(deviceId: number): Promise<boolean>;
  isCameraOpened(): Promise<boolean>;
  openCamera(): Promise<void>;
  closeCamera(): Promise<void>;

  // Preview
  getAllPreviewSizes(): Promise<PreviewSize[]>;
  getCurrentResolution(): Promise<PreviewSize>;
  updateResolution(width: number, height: number): Promise<void>;

  // Capture
  captureImage(path?: string | null): Promise<string>;

  // Video recording
  startRecording(path?: string | null, durationSec?: number): Promise<void>;
  stopRecording(): Promise<void>;
  isRecording(): Promise<boolean>;

  // Audio recording
  startAudioRecording(path?: string | null): Promise<void>;
  stopAudioRecording(): Promise<void>;

  // Camera controls
  getSupportedControls(): Promise<SupportedControls>;
  setBrightness(value: number): void;
  getBrightness(): Promise<number>;
  setContrast(value: number): void;
  getContrast(): Promise<number>;
  setZoom(value: number): void;
  getZoom(): Promise<number>;
  setSharpness(value: number): void;
  getSharpness(): Promise<number>;
  setSaturation(value: number): void;
  getSaturation(): Promise<number>;
  setHue(value: number): void;
  getHue(): Promise<number>;
  setGamma(value: number): void;
  getGamma(): Promise<number>;
  setGain(value: number): void;
  getGain(): Promise<number>;
  setAutoFocus(enable: boolean): void;
  setAutoWhiteBalance(enable: boolean): void;
  resetAllControls(): void;
}

// ── Module Events ────────────────────────────────────────────────────────

export type UsbCameraEventType =
  | 'onDeviceAttached'
  | 'onDeviceDetached'
  | 'onRecordingComplete'
  | 'onAudioRecordingComplete';
