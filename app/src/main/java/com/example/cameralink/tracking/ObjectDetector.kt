package com.example.cameralink.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Real-time object detection using TensorFlow Lite
 * Detects people and animals (birds) in camera frames
 */
class ObjectDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val detectedObjects = mutableListOf<DetectedObject>()
    private var nextObjectId = 0

    // Model configuration
    private val INPUT_SIZE = 320
    private val CONFIDENCE_THRESHOLD = 0.5f
    private val CLASSES = listOf("person", "bird", "cat", "dog", "horse", "cow")
    
    private val modelFileName = "efficientdet_lite0.tflite"
    private val modelUrl = "https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/1?format=tflite"

    init {
        try {
            loadModel()
            Log.d("ObjectDetector", "TensorFlow Lite model loaded successfully")
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Failed to load model: ${e.message}")
            // Try to download if local file doesn't exist
            downloadModelIfNeeded()
        }
    }

    private fun loadModel() {
        val modelFile = File(context.filesDir, modelFileName)
        
        // First try local assets
        try {
            val modelAssets = context.assets.openFd(modelFileName)
            val inputStream = FileInputStream(modelAssets.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelAssets.startOffset
            val declaredLength = modelAssets.declaredLength
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(buffer)
            Log.d("ObjectDetector", "Model loaded from assets")
            return
        } catch (e: Exception) {
            Log.d("ObjectDetector", "Model not in assets, checking app files: ${e.message}")
        }
        
        // Then try app files directory
        if (modelFile.exists()) {
            try {
                val buffer = FileInputStream(modelFile).channel.map(
                    FileChannel.MapMode.READ_ONLY, 0, modelFile.length()
                )
                interpreter = Interpreter(buffer)
                Log.d("ObjectDetector", "Model loaded from app files")
                return
            } catch (e: Exception) {
                Log.e("ObjectDetector", "Failed to load from app files: ${e.message}")
            }
        }
        
        throw Exception("Model file not found. Please add $modelFileName to assets or download it.")
    }

    private fun downloadModelIfNeeded() {
        val modelFile = File(context.filesDir, modelFileName)
        
        if (modelFile.exists() && modelFile.length() > 1000000) {
            Log.d("ObjectDetector", "Model already downloaded")
            try {
                loadModel()
            } catch (e: Exception) {
                Log.e("ObjectDetector", "Failed to load downloaded model: ${e.message}")
            }
            return
        }
        
        Log.d("ObjectDetector", "Starting model download from: $modelUrl")
        
        try {
            val url = URL(modelUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            val inputStream = connection.getInputStream()
            val fileOutputStream = FileOutputStream(modelFile)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fileOutputStream.write(buffer, 0, bytesRead)
            }
            
            fileOutputStream.close()
            inputStream.close()
            
            Log.d("ObjectDetector", "Model downloaded successfully: ${modelFile.length()} bytes")
            
            // Try loading again
            loadModel()
            
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Failed to download model: ${e.message}")
            Log.w("ObjectDetector", "Please manually add $modelFileName to app/src/main/assets/")
        }
    }

    /**
     * Detect objects in the given bitmap
     */
    fun detectObjects(bitmap: Bitmap): List<DetectedObject> {
        if (interpreter == null) return emptyList()

        return try {
            // Preprocess input
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = preprocessImage(resizedBitmap)

            // Run inference
            val outputDetectionBoxes = TensorBuffer.createFixedSize(intArrayOf(1, 25, 4), DataType.FLOAT32)
            val outputDetectionClasses = TensorBuffer.createFixedSize(intArrayOf(1, 25), DataType.FLOAT32)
            val outputDetectionScores = TensorBuffer.createFixedSize(intArrayOf(1, 25), DataType.FLOAT32)
            val outputNumDetections = TensorBuffer.createFixedSize(intArrayOf(1), DataType.FLOAT32)

            val outputs = mapOf(
                0 to outputDetectionBoxes.buffer,
                1 to outputDetectionClasses.buffer,
                2 to outputDetectionScores.buffer,
                3 to outputNumDetections.buffer
            )

            interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer.buffer), outputs)

            // Postprocess output
            postprocessDetections(
                outputDetectionBoxes.floatArray,
                outputDetectionClasses.floatArray,
                outputDetectionScores.floatArray,
                bitmap.width,
                bitmap.height
            )
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Detection error: ${e.message}")
            emptyList()
        }
    }

    private fun preprocessImage(bitmap: Bitmap): TensorBuffer {
        val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3), DataType.UINT8)
        val pixelValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixelValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val tensorData = ByteArray(INPUT_SIZE * INPUT_SIZE * 3)
        for (i in pixelValues.indices) {
            val pixel = pixelValues[i]
            tensorData[i * 3] = (pixel shr 16 and 0xFF).toByte()
            tensorData[i * 3 + 1] = (pixel shr 8 and 0xFF).toByte()
            tensorData[i * 3 + 2] = (pixel and 0xFF).toByte()
        }
        inputBuffer.loadBuffer(TensorBuffer.createFixedSize(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3), DataType.UINT8))
        return inputBuffer
    }

    private fun postprocessDetections(
        detectionBoxes: FloatArray,
        detectionClasses: FloatArray,
        detectionScores: FloatArray,
        imageWidth: Int,
        imageHeight: Int
    ): List<DetectedObject> {
        val results = mutableListOf<DetectedObject>()

        for (i in detectionScores.indices) {
            val confidence = detectionScores[i]
            if (confidence > CONFIDENCE_THRESHOLD) {
                val classId = detectionClasses[i].toInt()
                if (classId in CLASSES.indices) {
                    val boxIndex = i * 4
                    val top = detectionBoxes[boxIndex]
                    val left = detectionBoxes[boxIndex + 1]
                    val bottom = detectionBoxes[boxIndex + 2]
                    val right = detectionBoxes[boxIndex + 3]

                    val boundingBox = RectF(
                        left * imageWidth,
                        top * imageHeight,
                        right * imageWidth,
                        bottom * imageHeight
                    )

                    results.add(
                        DetectedObject(
                            id = nextObjectId++,
                            label = CLASSES[classId],
                            confidence = confidence,
                            boundingBox = boundingBox
                        )
                    )
                }
            }
        }

        detectedObjects.clear()
        detectedObjects.addAll(results)
        return results
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
