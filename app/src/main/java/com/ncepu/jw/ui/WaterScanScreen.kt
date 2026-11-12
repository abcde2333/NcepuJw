package com.ncepu.jw.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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
 * 相机扫码页(统一形态):全屏取景 + 四角取景框 + 扫描线 + 手电筒。
 *
 * 三个入口共用:waterscan(饮水机)、washerscan(洗衣机)、scan(自动分流),
 * 只有标题不同,遮罩/取景框/提示完全一致,避免三套观感。
 */
@Composable
fun WaterScanScreen(
    title: String = "扫描二维码",
    hint: String = "将设备二维码放入框内即可自动识别",
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = MaterialTheme.colorScheme.primary

    // 扫码页是深色全屏:系统栏图标必须转白,退出时按当前主题底色恢复
    // (外层只按主题明暗设过一次,浅色主题进扫码页会出现深底深图标)
    val view = LocalView.current
    val lightBarsForTheme = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = lightBarsForTheme
            controller?.isAppearanceLightNavigationBars = lightBarsForTheme
        }
    }

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var done by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var hasFlash by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    val providerRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val scannerRef = remember { arrayOfNulls<com.google.mlkit.vision.barcode.BarcodeScanner>(1) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // 离开页面必须解绑:否则相机(含手电筒)会在后台继续占着,退回上一页仍亮灯
    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraControl?.enableTorch(false) }
            runCatching { providerRef[0]?.unbindAll() }
            runCatching { scannerRef[0]?.close() }
            runCatching { executor.shutdown() }
        }
    }

    LaunchedEffect(torchOn) {
        val c = cameraControl ?: return@LaunchedEffect
        runCatching { c.enableTorch(torchOn) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val shortSide: Dp = if (maxWidth < maxHeight) maxWidth else maxHeight
        // 取景框:短边 72%(约 300dp),竖向居中略偏上,给手电筒与提示留位
        val side: Dp = shortSide * 0.72f
        val frameLeft: Dp = (maxWidth - side) / 2
        val frameTop: Dp = ((maxHeight - side) / 2 - 24.dp).coerceAtLeast(120.dp)
        val frameRadius: Dp = 18.dp

        if (granted) {
            AndroidView(
                factory = { context ->
                    val previewView = PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        providerRef[0] = provider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
                                .build()
                        )
                        scannerRef[0] = scanner
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
                        val camera = provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis,
                        )
                        cameraControl = camera.cameraControl
                        hasFlash = camera.cameraInfo.hasFlashUnit()
                    }, ContextCompat.getMainExecutor(context))
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 遮罩 + 取景框 + 扫描线(相机预览之上、控件之下)
        ScanOverlay(
            side = side,
            frameLeft = frameLeft,
            frameTop = frameTop,
            frameRadius = frameRadius,
            accent = primary,
            animate = granted && !done,
        )

        // 顶栏:返回 + 标题
        Column(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 6.dp, top = 2.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                style = TextStyle(
                    shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 8f),
                ),
            )
        }

        // 手电筒:取景框正下方,白色半透明圆钮
        if (granted && hasFlash) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = frameLeft + (side - 54.dp) / 2,
                        y = frameTop + side + 30.dp,
                    )
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (torchOn) 0.92f else 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable { torchOn = !torchOn },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    contentDescription = if (torchOn) "关闭手电筒" else "打开手电筒",
                    tint = if (torchOn) Color(0xFF1B1B1F) else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // 底部提示
        Text(
            text = if (done) "已识别" else hint,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 44.dp, start = 24.dp, end = 24.dp),
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 6f)),
        )

        if (!granted) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("需要相机权限才能扫码", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("授予权限")
                }
            }
        }
    }
}

/**
 * 扫码页叠层:框外压暗、框内四角括号 + 一条上下扫的扫描线。
 * 单独抽出来,避免相机预览每次重组都连带重画。
 */
@Composable
private fun ScanOverlay(
    side: Dp,
    frameLeft: Dp,
    frameTop: Dp,
    frameRadius: Dp,
    accent: Color,
    animate: Boolean,
) {
    val progress = if (animate) {
        val transition = rememberInfiniteTransition(label = "scanline")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "scanlineProgress",
        )
        p
    } else {
        0f
    }

    Canvas(Modifier.fillMaxSize()) {
        val left = frameLeft.toPx()
        val top = frameTop.toPx()
        val s = side.toPx()
        val radius = frameRadius.toPx()
        val right = left + s
        val bottom = top + s

        // 框外压暗(挖洞,不用四块黑条拼)
        val outer = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val hole = Path().apply {
            addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(radius)))
        }
        drawPath(
            path = Path.combine(PathOperation.Difference, outer, hole),
            color = Color.Black.copy(alpha = 0.62f),
        )

        // 顶部/底部再压一道竖向渐变,保证标题与提示在浅色背景上可读
        val fade = size.height * 0.16f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                startY = 0f,
                endY = fade,
            ),
            size = Size(size.width, fade),
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                startY = size.height - fade,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
        )

        // 框线:细白边,让浅色背景上也能看出框
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset(left, top),
            size = Size(s, s),
            cornerRadius = CornerRadius(radius),
            style = Stroke(width = 1.dp.toPx()),
        )

        // 四角括号:主色,带一层柔光
        val arm = (s * 0.14f).coerceIn(18.dp.toPx(), 34.dp.toPx())
        val stroke = 3.5.dp.toPx()
        val glow = 8.dp.toPx()
        fun bracket(x: Float, y: Float, sx: Float, sy: Float) {
            val hEnd = Offset(x + sx * arm, y)
            val vEnd = Offset(x, y + sy * arm)
            drawLine(accent.copy(alpha = 0.22f), Offset(x, y), hEnd, glow, StrokeCap.Round)
            drawLine(accent.copy(alpha = 0.22f), Offset(x, y), vEnd, glow, StrokeCap.Round)
            drawLine(accent, Offset(x, y), hEnd, stroke, StrokeCap.Round)
            drawLine(accent, Offset(x, y), vEnd, stroke, StrokeCap.Round)
        }
        bracket(left, top, 1f, 1f)
        bracket(right, top, -1f, 1f)
        bracket(left, bottom, 1f, -1f)
        bracket(right, bottom, -1f, -1f)

        // 扫描线:主色渐变 + 上侧一层更淡的拖尾
        if (animate) {
            val y = top + s * progress
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.85f), Color.Transparent),
                    startX = left + 6.dp.toPx(),
                    endX = right - 6.dp.toPx(),
                ),
                start = Offset(left + 6.dp.toPx(), y),
                end = Offset(right - 6.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.14f)),
                    startY = y - 26.dp.toPx(),
                    endY = y,
                ),
                topLeft = Offset(left + 6.dp.toPx(), y - 26.dp.toPx()),
                size = Size(s - 12.dp.toPx(), 26.dp.toPx()),
            )
        }
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

/** 从 Compose 的 Context 链里取到 Activity(可能被 ContextThemeWrapper 包过) */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return c as? android.app.Activity
}
