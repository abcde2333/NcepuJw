package com.ncepu.jw.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** 从二维码/链接内容提取设备编号(did/deviceId/device_id/id,或路径尾段) */
object WaterDeviceIdParser {
    private val keyPattern = Regex(
        """(?:[?&#;,\s{]|^)["']?(?:did|deviceId|device_id|id)["']?\s*(?:=|:)\s*["']?([^&#/,}\s"']+)""",
        RegexOption.IGNORE_CASE,
    )
    private val strongPattern = Regex("""\d{6,32}""")

    fun normalize(raw: String): String {
        val input = raw.trim()
        if (input.isBlank()) return ""
        val fromKey = keyPattern.find(input)?.groupValues?.get(1)
        if (fromKey != null) return fromKey.removeSurrounding("\"").removeSurrounding("'")
        // hnkzy.com 系链接:取路径中最后一个纯数字段
        if (input.contains("hnkzy.com") || input.contains("ilife798")) {
            val candidates = Regex("""\d{6,32}""").findAll(input).toList()
            if (candidates.isNotEmpty()) return candidates.last().value
        }
        // 纯数字内容直接作为 did
        if (input.matches(Regex("""\d{6,32}"""))) return input
        return input.take(64)
    }

    fun isLikelyDeviceId(s: String): Boolean = s.matches(strongPattern) || s.length in 4..40
}

/**
 * 相机扫码页:识别二维码内容后回调原始文本(由调用方用 WaterDeviceIdParser 归一化)。
 */
@Composable
fun WaterScanScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var done by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { context ->
                    val previewView = PreviewView(context)
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
                                .build()
                        )
                        val analysis = ImageAnalysis.Builder()
                            // 限到 720p:ML Kit 扫码足够,整帧分析负载和发热都显著下降
                            .setResolutionSelector(
                                androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                                    .setResolutionStrategy(
                                        androidx.camera.core.resolutionselector.ResolutionStrategy(
                                            android.util.Size(1280, 720),
                                            androidx.camera.core.resolutionselector.ResolutionStrategy
                                                .FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                        )
                                    )
                                    .build()
                            )
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy ->
                            processFrame(imageProxy, scanner) { raw ->
                                if (!done && raw.isNotBlank()) {
                                    done = true
                                    val did = WaterDeviceIdParser.normalize(raw)
                                    onMain {
                                        onResult(if (WaterDeviceIdParser.isLikelyDeviceId(did)) did else raw)
                                    }
                                }
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                        )
                    }, ContextCompat.getMainExecutor(context))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.padding(40.dp))
                Text("需要相机权限才能扫码", color = Color.White)
                Spacer(Modifier.padding(8.dp))
                androidx.compose.material3.Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("授予权限")
                }
            }
        }

        IconButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "取消", tint = Color.White)
        }
        Text(
            "对准机身二维码",
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
        )
    }
}

private fun processFrame(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onRaw: (String) -> Unit,
) {
    val media = imageProxy.image
    if (media == null) {
        imageProxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstOrNull()?.rawValue?.let(onRaw)
        }
        .addOnCompleteListener { imageProxy.close() }
}

private fun onMain(block: () -> Unit) {
    android.os.Handler(android.os.Looper.getMainLooper()).post(block)
}
