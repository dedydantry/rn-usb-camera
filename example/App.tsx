import React, {useEffect, useState, useCallback} from 'react';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  Alert,
  Platform,
  PermissionsAndroid,
  ActivityIndicator,
} from 'react-native';
import {UsbCameraView, UsbCamera} from 'rn-usb-camera';
import type {
  UsbDevice,
  PreviewSize,
  DeviceAttachedEvent,
  DeviceDetachedEvent,
  CameraErrorEvent,
  CameraLoadingEvent,
} from 'rn-usb-camera';

const isSupported = Platform.OS === 'android';

function App(): React.JSX.Element {
  const [devices, setDevices] = useState<UsbDevice[]>([]);
  const [cameraOpen, setCameraOpen] = useState(false);
  const [cameraLoading, setCameraLoading] = useState(false);
  const [recording, setRecording] = useState(false);
  const [previewSizes, setPreviewSizes] = useState<PreviewSize[]>([]);
  const [lastImage, setLastImage] = useState<string | null>(null);
  const [status, setStatus] = useState('Waiting for USB camera...');
  const [permissionsGranted, setPermissionsGranted] = useState(false);

  useEffect(() => {
    async function requestPermissions() {
      if (Platform.OS !== 'android') return;
      try {
        const permissions: Array<
          (typeof PermissionsAndroid.PERMISSIONS)[keyof typeof PermissionsAndroid.PERMISSIONS]
        > = [
          PermissionsAndroid.PERMISSIONS.CAMERA,
          PermissionsAndroid.PERMISSIONS.RECORD_AUDIO,
        ];

        // Android 12 and below need WRITE_EXTERNAL_STORAGE
        const apiLevel = Platform.Version;
        if (typeof apiLevel === 'number' && apiLevel < 33) {
          permissions.push(
            PermissionsAndroid.PERMISSIONS.WRITE_EXTERNAL_STORAGE,
          );
        }

        const granted = await PermissionsAndroid.requestMultiple(permissions);
        const allGranted = Object.values(granted).every(
          (v) => v === PermissionsAndroid.RESULTS.GRANTED,
        );
        setPermissionsGranted(allGranted);
        if (!allGranted) {
          const denied = Object.entries(granted)
            .filter(([_, v]) => v !== PermissionsAndroid.RESULTS.GRANTED)
            .map(([k]) => k.split('.').pop())
            .join(', ');
          setStatus(`Permissions denied: ${denied}`);
        }
      } catch (err) {
        console.warn('Permission request error:', err);
      }
    }
    requestPermissions();
  }, []);

  useEffect(() => {
    const sub1 = UsbCamera.addListener('onRecordingComplete', (data) => {
      Alert.alert('Recording Complete', data.path);
      setRecording(false);
    });
    const sub2 = UsbCamera.addListener(
      'onAudioRecordingComplete',
      (data) => {
        Alert.alert('Audio Recording Complete', data.path);
      },
    );
    return () => {
      sub1.remove();
      sub2.remove();
    };
  }, []);

  const refreshDevices = useCallback(async () => {
    const list = await UsbCamera.getDeviceList();
    setDevices(list);
  }, []);

  const onDeviceAttached = useCallback(
    (e: DeviceAttachedEvent) => {
      setStatus('Device attached: ' + e.nativeEvent.deviceName);
      refreshDevices();
    },
    [refreshDevices],
  );

  const onDeviceDetached = useCallback(
    (e: DeviceDetachedEvent) => {
      setStatus('Device detached: ' + e.nativeEvent.deviceId);
      setCameraOpen(false);
      refreshDevices();
    },
    [refreshDevices],
  );

  const onCameraOpened = useCallback(() => {
    setStatus('Camera opened');
    setCameraOpen(true);
    setCameraLoading(false);
    UsbCamera.getAllPreviewSizes().then(setPreviewSizes);
  }, []);

  const onCameraClosed = useCallback(() => {
    setStatus('Camera closed');
    setCameraOpen(false);
  }, []);

  const onCameraLoading = useCallback((e: CameraLoadingEvent) => {
    setStatus('Camera loading...');
    setCameraLoading(true);
  }, []);

  const onError = useCallback((e: CameraErrorEvent) => {
    setStatus('Error: ' + e.nativeEvent.message);
  }, []);

  const handleCapture = async () => {
    try {
      const path = await UsbCamera.captureImage(null);
      setLastImage(path);
      console.log(path, 'slsl')
      Alert.alert('Image Captured', path);
    } catch (err: any) {
      Alert.alert('Capture Error', err.message);
    }
  };

  const handleToggleRecording = async () => {
    try {
      if (recording) {
        await UsbCamera.stopRecording();
        setRecording(false);
      } else {
        await UsbCamera.startRecording(null, 0);
        setRecording(true);
      }
    } catch (err: any) {
      Alert.alert('Recording Error', err.message);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#111" />

      <View style={styles.header}>
        <Text style={styles.title}>USB Camera Demo</Text>
        <Text style={styles.subtitle}>
          {isSupported ? status : 'USB Camera not supported on this platform'}
        </Text>
      </View>

      <View style={styles.previewContainer}>
        <UsbCameraView
          style={styles.preview}
          previewWidth={1280}
          previewHeight={720}
          autoOpen={true}
          onDeviceAttached={onDeviceAttached}
          onDeviceDetached={onDeviceDetached}
          onCameraOpened={onCameraOpened}
          onCameraClosed={onCameraClosed}
          onCameraLoading={onCameraLoading}
          onError={onError}
        />
        {cameraLoading && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color="#fff" />
            <Text style={styles.loadingText}>Changing resolution...</Text>
          </View>
        )}
      </View>

      <ScrollView
        style={styles.controls}
        contentContainerStyle={styles.controlsContent}>
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>
            Devices ({devices.length})
          </Text>
          <TouchableOpacity style={styles.btn} onPress={refreshDevices}>
            <Text style={styles.btnText}>Refresh Devices</Text>
          </TouchableOpacity>
          {devices.map((d) => (
            <Text key={d.deviceId} style={styles.info}>
              #{d.deviceId} - {d.deviceName} (VID:{d.vendorId} PID:
              {d.productId})
            </Text>
          ))}
        </View>

        {cameraOpen && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Actions</Text>
            <View style={styles.btnRow}>
              <TouchableOpacity
                style={[styles.btn, styles.btnPrimary]}
                onPress={handleCapture}>
                <Text style={styles.btnText}>Capture</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[
                  styles.btn,
                  recording ? styles.btnDanger : styles.btnPrimary,
                ]}
                onPress={handleToggleRecording}>
                <Text style={styles.btnText}>
                  {recording ? 'Stop Recording' : 'Start Recording'}
                </Text>
              </TouchableOpacity>
            </View>
            {lastImage && (
              <Text style={styles.info}>Last capture: {lastImage}</Text>
            )}
          </View>
        )}

        {previewSizes.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>
              Available Sizes ({previewSizes.length})
            </Text>
            <View style={styles.sizeList}>
              {previewSizes.map((s, i) => (
                <TouchableOpacity
                  key={i}
                  style={styles.sizeChip}
                  onPress={() =>
                    UsbCamera.updateResolution(s.width, s.height)
                  }>
                  <Text style={styles.sizeText}>
                    {s.width}x{s.height}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1, backgroundColor: '#111'},
  header: {paddingHorizontal: 16, paddingVertical: 12},
  title: {color: '#fff', fontSize: 20, fontWeight: '700'},
  subtitle: {color: '#aaa', fontSize: 13, marginTop: 4},
  previewContainer: {
    height: 260,
    marginHorizontal: 12,
    borderRadius: 12,
    overflow: 'hidden',
    backgroundColor: '#000',
  },
  preview: {flex: 1},
  loadingOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.7)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  loadingText: {color: '#fff', fontSize: 13, marginTop: 10},
  controls: {flex: 1},
  controlsContent: {padding: 16, paddingBottom: 40},
  section: {marginBottom: 20},
  sectionTitle: {
    color: '#fff',
    fontSize: 15,
    fontWeight: '600',
    marginBottom: 8,
  },
  btnRow: {flexDirection: 'row', gap: 10},
  btn: {
    backgroundColor: '#333',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    marginBottom: 8,
  },
  btnPrimary: {backgroundColor: '#0a84ff'},
  btnDanger: {backgroundColor: '#ff453a'},
  btnText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
  info: {color: '#888', fontSize: 12, marginTop: 4},
  sizeList: {flexDirection: 'row', flexWrap: 'wrap', gap: 6},
  sizeChip: {
    backgroundColor: '#222',
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#444',
  },
  sizeText: {color: '#ccc', fontSize: 12},
});

export default App;
