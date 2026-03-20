import React from 'react';
import {
  requireNativeComponent,
  UIManager,
  Platform,
  View,
  Text,
  StyleSheet,
} from 'react-native';
import type { UsbCameraViewProps } from './types';

const COMPONENT_NAME = 'RnUsbCameraView';

let cachedNativeView: React.ComponentType<UsbCameraViewProps> | null | undefined;

function resolveNativeView(): React.ComponentType<UsbCameraViewProps> | null {
  if (Platform.OS !== 'android') {
    return null;
  }

  if (cachedNativeView !== undefined) {
    return cachedNativeView;
  }

  try {
    const hasViewManagerConfig =
      typeof UIManager.getViewManagerConfig === 'function' &&
      UIManager.getViewManagerConfig(COMPONENT_NAME) != null;

    cachedNativeView = hasViewManagerConfig
      ? requireNativeComponent<UsbCameraViewProps>(COMPONENT_NAME)
      : null;
  } catch (error) {
    console.warn(`[rn-usb-camera] Failed to resolve ${COMPONENT_NAME}`, error);
    cachedNativeView = null;
  }

  return cachedNativeView;
}

/**
 * USB Camera preview component.
 *
 * Displays a live camera preview from a connected USB (UVC) camera.
 * The camera automatically opens when a USB device is attached (if autoOpen is true).
 *
 * On iOS or other unsupported platforms, renders a placeholder view.
 * You can customize the fallback by passing `children`.
 *
 * @example
 * ```tsx
 * <UsbCameraView
 *   style={{ flex: 1 }}
 *   previewWidth={1280}
 *   previewHeight={720}
 *   resizeMode="cover"
 *   autoOpen={true}
 *   onDeviceAttached={(e) => console.log('Attached:', e.nativeEvent)}
 *   onCameraOpened={() => console.log('Camera opened')}
 *   onError={(e) => console.log('Error:', e.nativeEvent.message)}
 * />
 *
 * // Custom fallback for iOS:
 * <UsbCameraView style={{ flex: 1 }}>
 *   <Text>USB Camera is not supported on this device.</Text>
 * </UsbCameraView>
 * ```
 */
export const UsbCameraView: React.FC<UsbCameraViewProps> = (props) => {
  const { style, children, ...rest } = props;
  const NativeView = resolveNativeView();

  if (Platform.OS !== 'android' || NativeView == null) {
    return (
      <View style={[styles.unsupported, style]}>
        {children ?? (
          <>
            <Text style={styles.unsupportedIcon}>\u26A0\uFE0F</Text>
            <Text style={styles.unsupportedTitle}>USB Camera not available</Text>
            <Text style={styles.unsupportedSubtitle}>
              USB UVC cameras are only supported on Android devices with USB OTG.
            </Text>
          </>
        )}
      </View>
    );
  }

  return <NativeView style={style} {...rest} />;
};

const styles = StyleSheet.create({
  unsupported: {
    backgroundColor: '#1a1a1a',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  unsupportedIcon: {
    fontSize: 40,
    marginBottom: 12,
  },
  unsupportedTitle: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  unsupportedSubtitle: {
    color: '#999999',
    fontSize: 13,
    textAlign: 'center',
    lineHeight: 18,
  },
});
