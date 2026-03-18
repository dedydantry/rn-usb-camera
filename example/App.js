import { StatusBar } from 'expo-status-bar';
import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  PermissionsAndroid,
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  useWindowDimensions,
  View,
} from 'react-native';
import { UsbCamera, UsbCameraView, isSupported } from 'rn-usb-camera';

const ORIENTATION_OPTIONS = [
  { label: 'Landscape', value: '0' },
  { label: 'Portrait Right', value: '90' },
  { label: 'Upside Down', value: '180' },
  { label: 'Portrait Left', value: '270' },
];

export default function App() {
  const [devices, setDevices] = useState([]);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [recording, setRecording] = useState(false);
  const [previewSizes, setPreviewSizes] = useState([]);
  const [previewSize, setPreviewSize] = useState({ width: 1280, height: 720 });
  const [previewRotation, setPreviewRotation] = useState('0');
  const [lastCapture, setLastCapture] = useState(null);
  const [status, setStatus] = useState('Waiting for USB camera...');
  const [permissionsGranted, setPermissionsGranted] = useState(Platform.OS !== 'android');
  const { width: screenWidth } = useWindowDimensions();

  const isPortraitPreview = previewRotation === '90' || previewRotation === '270';
  const previewAspectRatio = isPortraitPreview
    ? previewSize.height / previewSize.width
    : previewSize.width / previewSize.height;
  const previewCardHeight = isPortraitPreview
    ? Math.min(screenWidth * 1.15, 540)
    : Math.min(screenWidth * 0.62, 320);


  useEffect(() => {
    async function requestPermissions() {
      if (Platform.OS !== 'android') {
        return;
      }

      try {
        const permissions = [
          PermissionsAndroid.PERMISSIONS.CAMERA,
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
        ];

        if (typeof Platform.Version === 'number' && Platform.Version < 33) {
          permissions.push(PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE);
        }

        const result = await PermissionsAndroid.requestMultiple(permissions);
        const granted = Object.values(result).every(
          (value) => value === PermissionsAndroid.RESULTS.GRANTED
        );

        setPermissionsGranted(granted);
        if (!granted) {
          setStatus('Android runtime permissions are required.');
        }
      } catch (error) {
        setStatus(error instanceof Error ? error.message : 'Failed to request permissions.');
      }
    }

    requestPermissions();
  }, []);

  useEffect(() => {
    const recordingSubscription = UsbCamera.addListener('onRecordingComplete', ({ path }) => {
      setRecording(false);
      Alert.alert('Recording Complete', path);
    });
    const audioSubscription = UsbCamera.addListener('onAudioRecordingComplete', ({ path }) => {
      Alert.alert('Audio Recording Complete', path);
    });

    return () => {
      recordingSubscription.remove();
      audioSubscription.remove();
    };
  }, []);

  const refreshDevices = useCallback(async () => {
    try {
      const deviceList = await UsbCamera.getDeviceList();
      setDevices(deviceList);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to load USB devices.');
    }
  }, []);

  const refreshPreviewSizes = useCallback(async () => {
    try {
      const sizes = await UsbCamera.getAllPreviewSizes();
      setPreviewSizes(sizes);
    } catch {
      setPreviewSizes([]);
    }
  }, []);

  const handleCapture = useCallback(async () => {
    try {
      const path = await UsbCamera.captureImage();
      setLastCapture(path);
      Alert.alert('Image Captured', path);
    } catch (error) {
      Alert.alert('Capture Error', error instanceof Error ? error.message : 'Unknown capture error');
    }
  }, []);

  const handleToggleRecording = useCallback(async () => {
    try {
      if (recording) {
        await UsbCamera.stopRecording();
        setRecording(false);
        return;
      }

      await UsbCamera.startRecording(null, 0);
      setRecording(true);
    } catch (error) {
      Alert.alert('Recording Error', error instanceof Error ? error.message : 'Unknown recording error');
    }
  }, [recording]);

  const handleResolution = useCallback(async (width, height) => {
    try {
      await UsbCamera.updateResolution(width, height);
      setPreviewSize({ width, height });
      setStatus(`Resolution changed to ${width}x${height}`);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : 'Failed to change resolution.');
    }
  }, []);

  const handleDeviceAttached = useCallback(
    async ({ nativeEvent }) => {
      setStatus(`Device attached: ${nativeEvent.deviceName}`);
      await refreshDevices();
    },
    [refreshDevices]
  );

  const handleDeviceDetached = useCallback(
    async ({ nativeEvent }) => {
      setStatus(`Device detached: ${nativeEvent.deviceId}`);
      setCameraOpen(false);
      await refreshDevices();
    },
    [refreshDevices]
  );

  const handleCameraOpened = useCallback(async () => {
    setCameraOpen(true);
    setCameraLoading(false);
    setStatus('Camera opened');
    await refreshPreviewSizes();
  }, [refreshPreviewSizes]);

  const handleCameraClosed = useCallback(() => {
    setCameraOpen(false);
    setStatus('Camera closed');
  }, []);

  const handleCameraLoading = useCallback(() => {
    setCameraLoading(true);
    setStatus('Opening or updating camera...');
  }, []);

  const handleError = useCallback(({ nativeEvent }) => {
    setCameraLoading(false);
    setStatus(`Error: ${nativeEvent.message}`);
  }, []);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />

      <View style={styles.header}>
        <Text style={styles.title}>rn-usb-camera example</Text>
        <Text style={styles.subtitle}>{isSupported ? status : 'Android only'}</Text>
      </View>

      <View style={[styles.previewShell, { minHeight: previewCardHeight }] }>
        <View style={styles.previewBadgeRow}>
          <Text style={styles.previewBadge}>USB webcam canvas</Text>
          <Text style={styles.previewMeta}>
            {previewSize.width}x{previewSize.height} · {ORIENTATION_OPTIONS.find((option) => option.value === previewRotation)?.label}
          </Text>
        </View>
        <UsbCameraView
          style={[
            styles.preview,
            {
              aspectRatio: previewAspectRatio,
              // maxHeight: previewCardHeight,
            },
          ]}
          resizeMode="contain"
          previewRotation={previewRotation}
          autoOpen={permissionsGranted}
          onDeviceAttached={handleDeviceAttached}
          onDeviceDetached={handleDeviceDetached}
          onCameraOpened={handleCameraOpened}
          onCameraClosed={handleCameraClosed}
          onCameraLoading={handleCameraLoading}
          onError={handleError}
        />
        {cameraLoading ? (
          <View style={styles.overlay}>
            <ActivityIndicator color="#ffffff" size="large" />
            <Text style={styles.overlayText}>Loading camera...</Text>
          </View>
        ) : null}
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.card}>
          <Text style={styles.cardTitle}>Setup</Text>
          <Text style={styles.info}>Platform supported: {String(isSupported)}</Text>
          <Text style={styles.info}>Permissions granted: {String(permissionsGranted)}</Text>
          <TouchableOpacity style={styles.button} onPress={refreshDevices}>
            <Text style={styles.buttonText}>Refresh USB devices</Text>
          </TouchableOpacity>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Connected devices ({devices.length})</Text>
          {devices.length === 0 ? (
            <Text style={styles.info}>No USB camera detected.</Text>
          ) : (
            devices.map((device, i) => (
              <Text key={i} style={styles.info}>
                #{device.deviceId} {device.deviceName} (VID {device.vendorId}, PID {device.productId})
              </Text>
            ))
          )}
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Camera actions</Text>
          <Text style={styles.info}>Camera open: {String(cameraOpen)}</Text>
          <View style={styles.row}>
            <TouchableOpacity
              style={[styles.button, !cameraOpen && styles.buttonDisabled]}
              disabled={!cameraOpen}
              onPress={handleCapture}
            >
              <Text style={styles.buttonText}>Capture image</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, recording && styles.buttonDanger, !cameraOpen && styles.buttonDisabled]}
              disabled={!cameraOpen}
              onPress={handleToggleRecording}
            >
              <Text style={styles.buttonText}>{recording ? 'Stop recording' : 'Start recording'}</Text>
            </TouchableOpacity>
          </View>
          {lastCapture ? <Text style={styles.info}>Last capture: {lastCapture}</Text> : null}
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Webcam orientation</Text>
          <Text style={styles.info}>
            Rotate the preview canvas when the webcam sensor is mounted sideways.
          </Text>
          <View style={styles.chips}>
            {ORIENTATION_OPTIONS.map((option) => (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.chip,
                  previewRotation === option.value && styles.chipActive,
                ]}
                onPress={() => setPreviewRotation(option.value)}
              >
                <Text
                  style={[
                    styles.chipText,
                    previewRotation === option.value && styles.chipTextActive,
                  ]}
                >
                  {option.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Preview sizes</Text>
          <View style={styles.chips}>
            {previewSizes.map((size, i) => (
              <TouchableOpacity
                key={i}
                style={[
                  styles.chip,
                  previewSize.width === size.width &&
                    previewSize.height === size.height &&
                    styles.chipActive,
                ]}
                onPress={() => handleResolution(size.width, size.height)}
              >
                <Text
                  style={[
                    styles.chipText,
                    previewSize.width === size.width &&
                      previewSize.height === size.height &&
                      styles.chipTextActive,
                  ]}
                >
                  {size.width}x{size.height}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#09121f',
  },
  header: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 8,
  },
  title: {
    color: '#ffffff',
    fontSize: 24,
    fontWeight: '700',
  },
  subtitle: {
    color: '#9cb0c9',
    fontSize: 14,
    marginTop: 4,
  },
  previewShell: {
    marginHorizontal: 16,
    borderRadius: 20,
    overflow: 'hidden',
    backgroundColor: '#000000',
    padding: 12,
    borderWidth: 1,
    borderColor: '#20354b',
  },
  preview: {
    width: '100%',
    alignSelf: 'center',
    borderRadius: 16,
    overflow: 'hidden',
    backgroundColor: '#02060b',
  },
  previewBadgeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
    gap: 12,
  },
  previewBadge: {
    color: '#7fd1ff',
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 0.6,
    textTransform: 'uppercase',
  },
  previewMeta: {
    color: '#90a7c1',
    fontSize: 12,
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(9, 18, 31, 0.55)',
  },
  overlayText: {
    color: '#ffffff',
    marginTop: 12,
  },
  content: {
    padding: 16,
    gap: 12,
  },
  card: {
    backgroundColor: '#112033',
    borderRadius: 18,
    padding: 16,
    gap: 10,
  },
  cardTitle: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  info: {
    color: '#c6d4e3',
    fontSize: 13,
    lineHeight: 18,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
    flexWrap: 'wrap',
  },
  button: {
    backgroundColor: '#2d9cdb',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 11,
  },
  buttonDanger: {
    backgroundColor: '#d64545',
  },
  buttonDisabled: {
    backgroundColor: '#47617f',
  },
  buttonText: {
    color: '#ffffff',
    fontWeight: '600',
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  chip: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#315274',
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#112033',
  },
  chipActive: {
    backgroundColor: '#2d9cdb',
    borderColor: '#69c6ff',
  },
  chipText: {
    color: '#d7e5f4',
    fontSize: 12,
    fontWeight: '600',
  },
  chipTextActive: {
    color: '#ffffff',
  },
});
