# Target Tracking Feature Setup Guide

## Overview
This feature adds real-time object detection and tracking with motorized pan/tilt control.

**Capabilities:**
- ✅ Real-time object detection (people, animals, birds)
- ✅ Kalman filter-based tracking
- ✅ Motorized pan/tilt control
- ✅ Cloud-based detection API integration
- ✅ Async processing

## Files Added

```
app/src/main/java/com/example/cameralink/tracking/
├── DetectedObject.kt           # Data class for detected objects
├── TrackingState.kt            # UI state for tracking
├── ObjectDetector.kt           # TensorFlow Lite detection
├── KalmanFilter.kt             # Smooth tracking algorithm
├── TargetTracker.kt            # Multi-object association
├── MotorControlCommand.kt       # Motor control data class
├── MotorController.kt          # Motor hardware interface
├── CloudTrackingApi.kt         # Cloud detection API
└── TargetTrackingService.kt    # Main tracking service
```

## Setup Steps

### 1. Download TensorFlow Lite Model

```bash
# Download EfficientDet Lite 0 model
wget https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/1

# Extract and place in assets
cp efficientdet_lite0.tflite app/src/main/assets/
```

**Or use the model directly:**
- Go to [TensorFlow Hub](https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/1)
- Download the `.tflite` file
- Place in `app/src/main/assets/efficientdet_lite0.tflite`

### 2. Configure Cloud API (Optional)

Update the base URL in `CloudTrackingApi.kt`:

```kotlin
private val baseUrl: String = "http://your-tracking-api-server.com/api/v1/"
```

### 3. Configure Motor Hardware

Edit `MotorController.sendToHardware()` based on your hardware:

**Serial Communication (USB):**
```kotlin
private fun sendToHardware(command: String) {
    val serialPort = openSerialPort("/dev/ttyUSB0")
    serialPort.write(command.toByteArray())
}
```

**Bluetooth:**
```kotlin
private fun sendToHardware(command: String) {
    bluetoothSocket?.outputStream?.write(command.toByteArray())
}
```

**Network/WebSocket:**
```kotlin
private fun sendToHardware(command: String) {
    webSocket.send(command)
}
```

### 4. Integrate with CameraStreamingService

Add to `CameraStreamingService.kt`:

```kotlin
private val trackingService = TargetTrackingService()

private fun onFrameAvailable(imageProxy: ImageProxy) {
    trackingService.processFrame(imageProxy)
}
```

### 5. Add UI Controls

Add to `MainActivity.kt`:

```kotlin
Button(onClick = { trackingService.startTracking() }) {
    Text("Start Tracking")
}

Button(onClick = { trackingService.stopTracking() }) {
    Text("Stop Tracking")
}
```

## Usage

### Start Tracking
```kotlin
val service = TargetTrackingService()
service.startTracking()
```

### Select Target
```kotlin
service.selectTarget(objectId = 0)  // Select first detected object
```

### Listen to State Changes
```kotlin
service.trackingState.observe(this) { state ->
    Log.d("Tracking", "Pan: ${state.panAngle}°, Tilt: ${state.tiltAngle}°")
}

service.detectedObjects.observe(this) { objects ->
    // Update UI with detected objects
}
```

## Motor Control Command Format

Default command format (customize in `MotorController`):
```
PAN:45.5,TILT:30.2,SPEED:50
```

**Parameters:**
- `PAN`: -180 to 180 degrees (horizontal)
- `TILT`: -90 to 90 degrees (vertical)
- `SPEED`: 0-100 percent (motor speed)

## Performance Tuning

### Detection Confidence Threshold
`ObjectDetector.kt` line 30:
```kotlin
private val CONFIDENCE_THRESHOLD = 0.5f  // Adjust 0-1
```

### Tracking Distance Threshold
`TargetTracker.kt` line 18:
```kotlin
private val maxTrackingDistance = 100f  // pixels
```

### Kalman Filter Smoothing
`KalmanFilter.kt` line 65:
```kotlin
val weight = 0.3f  // 0 = pure prediction, 1 = pure measurement
```

## Hardware Requirements

**For Motorized Tracking:**
- Servo motors or stepper motors with control board
- Serial/USB/Bluetooth adapter for communication
- Motor driver (e.g., Arduino, Raspberry Pi Zero)
- Pan/tilt mechanical mount

**Recommended Hardware:**
- Pan/tilt bracket (~$30-50)
- 2x SG90 servo motors (~$10 each)
- Arduino Nano + servo shield (~$15)
- USB-to-TTL serial adapter (~$5)

**Software Interface Options:**
1. **Serial/USB** (simplest) - Direct commands via USB
2. **Bluetooth** - Wireless HC-05 module
3. **Network** - WebSocket/HTTP to motor controller
4. **GPIO** - Direct Raspberry Pi GPIO control

## Troubleshooting

### Model Not Loading
- Ensure `efficientdet_lite0.tflite` is in `app/src/main/assets/`
- Check file size (~4MB)
- Verify TensorFlow Lite dependencies are included

### High Latency
- Reduce frame rate (skip frames)
- Lower confidence threshold
- Disable cloud API while testing

### Motors Not Responding
- Check `sendToHardware()` implementation
- Verify serial/Bluetooth connection
- Check motor controller command format
- Monitor logcat for command output

## Next Steps

1. Implement hardware communication in `MotorController.sendToHardware()`
2. Test object detection with sample images
3. Tune Kalman filter parameters for your use case
4. Integrate tracking UI into MainActivity
5. Deploy to physical device for testing

## References

- [TensorFlow Lite Object Detection](https://www.tensorflow.org/lite/models/detect_objects_lite)
- [Kalman Filter Tutorial](https://en.wikipedia.org/wiki/Kalman_filter)
- [Android CameraX Guide](https://developer.android.com/training/camerax)
- [Retrofit HTTP Client](https://square.github.io/retrofit/)
