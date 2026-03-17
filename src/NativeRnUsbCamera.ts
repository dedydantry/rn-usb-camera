import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  // Device
  getDeviceList(): Promise<Object[]>;
  requestPermission(deviceId: number): Promise<boolean>;
  isCameraOpened(): Promise<boolean>;
  openCamera(): Promise<void>;
  closeCamera(): Promise<void>;

  // Preview
  getAllPreviewSizes(): Promise<Object[]>;
  getCurrentResolution(): Promise<Object>;
  updateResolution(width: number, height: number): Promise<void>;

  // Capture
  captureImage(path: string | null): Promise<string>;

  // Video recording
  startRecording(path: string | null, durationSec: number): Promise<void>;
  stopRecording(): Promise<void>;
  isRecording(): Promise<boolean>;

  // Audio recording
  startAudioRecording(path: string | null): Promise<void>;
  stopAudioRecording(): Promise<void>;

  // Camera controls
  getSupportedControls(): Promise<Object>;
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

  // Event emitter support
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('RnUsbCamera');
