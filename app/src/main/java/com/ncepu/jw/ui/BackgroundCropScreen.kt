package com.ncepu.jw.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 背景图裁剪:拖动/双指缩放取景,确认后按当前屏幕可视区域裁剪保存(所见即所得)。
 */
@Composable
fun BackgroundCropScreen(
    uri: Uri,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val screenWpx = with(density) { ctx.resources.displayMetrics.widthPixels.toFloat() }
    val screenHpx = with(density) { ctx.resources.displayMetrics.heightPixels.toFloat() }

    // 大图解码耗数十至上百毫秒,放 IO 线程;失败与加载中分开,避免加载期间误报"加载失败"
    var decoded by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    var rotation by remember(uri) { mutableIntStateOf(0) }
    // 显示与裁剪的基准图。旋转始终从原图重算(不叠加在上一张副本上),否则每转一次 JPEG 损一次
    var src by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { decodeSampled(ctx, uri) }
        decoded = bmp
        loadFailed = bmp == null
    }
    LaunchedEffect(decoded, rotation) {
        val d = decoded ?: return@LaunchedEffect
        val rotated = if (rotation == 0) d
        else withContext(Dispatchers.IO) { rotateBitmap(d, rotation) }
        val prev = src
        src = rotated
        // 只回收我们自己生成的副本;prev === d 时它是原图,不能动
        if (prev != null && prev !== d && prev !== rotated) prev.recycle()
    }

    val shown = src
    if (shown == null) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (loadFailed) {
                Text("图片加载失败", color = Color.White)
            } else {
                CircularProgressIndicator(color = Color.White)
            }
            IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, contentDescription = "返回", tint = Color.White)
            }
        }
        return
    }

    val img = shown.asImageBitmap()
    val imgW = img.width.toFloat()
    val imgH = img.height.toFloat()

    // cover 基准缩放:保证图覆盖全屏(状态只初始化一次,避免重组时重置手势)
    val minScale = max(screenWpx / imgW, screenHpx / imgH)
    val maxScale = minScale * 6f
    val baseOffset = Offset((screenWpx - imgW * minScale) / 2f, (screenHpx - imgH * minScale) / 2f)
    var scale by remember(src) { mutableStateOf(minScale) }
    var offset by remember(src) { mutableStateOf(baseOffset) }

    fun clampOffset() {
        val dw = imgW * scale
        val dh = imgH * scale
        val minX = screenWpx - dw
        val minY = screenHpx - dh
        offset = Offset(
            offset.x.coerceIn(minOf(0f, minX), maxOf(0f, minX)),
            offset.y.coerceIn(minOf(0f, minY), maxOf(0f, minY)),
        )
    }

    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(src) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        offset += pan
                        clampOffset()
                    }
                },
        ) {
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                dstSize = IntSize((imgW * scale).roundToInt(), (imgH * scale).roundToInt()),
            )
            // 九宫格构图参考:视口就是最终裁剪区域(所见即所得),所以按屏幕三等分画
            val guide = Color.White.copy(alpha = 0.28f)
            listOf(1f / 3f, 2f / 3f).forEach { f ->
                drawLine(guide, Offset(size.width * f, 0f), Offset(size.width * f, size.height), 1.dp.toPx())
                drawLine(guide, Offset(0f, size.height * f), Offset(size.width, size.height * f), 1.dp.toPx())
            }
        }

        // 底部工具卡(Material You):tonal 容器 surfaceContainerHigh + extraLarge 圆角,
        // 顶栏整条去掉,取景区域占满全屏。提示 / 取消 / 旋转 / 重置 / 保存全在这一张卡里。
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
                Text(
                    saveError ?: "拖动 / 双指缩放调整位置,确认后将按当前画面裁剪保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (saveError == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp, bottom = 2.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "取消",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { rotation = (rotation + 90) % 360 }) {
                        Icon(
                            Icons.Filled.Rotate90DegreesCw,
                            contentDescription = "顺时针旋转 90°",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 置灰而不是隐藏:按钮位置固定,手指不用重新找
                    IconButton(
                        onClick = {
                            scale = minScale
                            offset = baseOffset
                        },
                        enabled = scale != minScale || offset != baseOffset,
                    ) {
                        Icon(
                            Icons.Filled.Restore,
                            contentDescription = "重置取景",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    FilledIconButton(
                        onClick = {
                            if (saving) return@FilledIconButton
                            saving = true
                            saveError = null
                            // 屏幕可视区域 → 基准图像素
                            val srcX = (-offset.x / scale).roundToInt().coerceIn(0, img.width - 1)
                            val srcY = (-offset.y / scale).roundToInt().coerceIn(0, img.height - 1)
                            val srcW = (screenWpx / scale).roundToInt().coerceAtMost(img.width - srcX)
                            val srcH = (screenHpx / scale).roundToInt().coerceAtMost(img.height - srcY)
                            val cropped = Bitmap.createBitmap(shown, srcX, srcY, srcW, srcH)
                            val err = runCatching {
                                ctx.openFileOutput(
                                    "schedule_bg.jpg",
                                    android.content.Context.MODE_PRIVATE,
                                ).use {
                                    cropped.compress(Bitmap.CompressFormat.JPEG, 88, it)
                                }
                                ctx.getSharedPreferences("jw", android.content.Context.MODE_PRIVATE)
                                    .edit().putString("schedule_bg", "schedule_bg.jpg").apply()
                            }.exceptionOrNull()?.let { "保存失败:${it.message ?: "存储不可用"}" }
                            saving = false
                            // 失败必须留在本页:回调 onDone 会让用户以为已生效
                            if (err != null) {
                                saveError = err
                                return@FilledIconButton
                            }
                            onDone()
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        // 只留图标:主操作靠 primary 实心圆底区分,含义由上方提示行和 contentDescription 承担
                        Icon(Icons.Filled.Check, contentDescription = "保存背景")
                    }
                }
            }
        }
    }
}

/**
 * 旋转 90/180/270 度并返回新图。必须先用 mapRect 算旋转后的外接矩形:
 * createBitmap(src, 0, 0, w, h, matrix, true) 会保持原宽高,90° 时直接把四个角裁掉。
 */
private fun rotateBitmap(src: Bitmap, deg: Int): Bitmap {
    val matrix = Matrix().apply { postRotate(deg.toFloat()) }
    val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
    matrix.mapRect(bounds)
    matrix.postTranslate(-bounds.left, -bounds.top)
    val out = Bitmap.createBitmap(
        bounds.width().roundToInt().coerceAtLeast(1),
        bounds.height().roundToInt().coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    android.graphics.Canvas(out).drawBitmap(src, matrix, null)
    return out
}

/** 先量尺寸再降采样解码(单边不超 1600px,防止整图撑爆内存);失败返回 null */
private fun decodeSampled(ctx: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1600) sample *= 2
    ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()
