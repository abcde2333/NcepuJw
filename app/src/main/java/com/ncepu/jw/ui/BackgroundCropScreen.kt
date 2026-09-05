package com.ncepu.jw.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    val src = remember(uri) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1600) sample *= 2
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()
    }

    if (src == null) {
        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text("图片加载失败", color = Color.White)
            IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, contentDescription = "返回", tint = Color.White)
            }
        }
        return
    }

    val img = src.asImageBitmap()
    val imgW = img.width.toFloat()
    val imgH = img.height.toFloat()

    // cover 基准缩放:保证图覆盖全屏(状态只初始化一次,避免重组时重置手势)
    val minScale = max(screenWpx / imgW, screenHpx / imgH)
    val maxScale = minScale * 6f
    var scale by remember(src) { mutableStateOf(minScale) }
    var offset by remember(src) {
        mutableStateOf(Offset((screenWpx - imgW * minScale) / 2f, (screenHpx - imgH * minScale) / 2f))
    }

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
        }

        // 顶栏:仅取消与提示
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .background(Color.Black.copy(alpha = 0.35f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "取消", tint = Color.White)
            }
            Text("拖动/双指缩放调整背景位置", color = Color.White, fontSize = 13.sp)
        }

        // 确认按钮置于底部(大按钮,单手易点)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "确认后将按当前画面裁剪保存",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            androidx.compose.material3.Button(
                onClick = {
                    if (saving || src == null) return@Button
                    saving = true
                    // 屏幕可视区域 → 原图像素
                    val srcX = (-offset.x / scale).roundToInt().coerceIn(0, img.width - 1)
                    val srcY = (-offset.y / scale).roundToInt().coerceIn(0, img.height - 1)
                    val srcW = (screenWpx / scale).roundToInt().coerceAtMost(img.width - srcX)
                    val srcH = (screenHpx / scale).roundToInt().coerceAtMost(img.height - srcY)
                    val cropped = Bitmap.createBitmap(src, srcX, srcY, srcW, srcH)
                    runCatching {
                        ctx.openFileOutput("schedule_bg.jpg", android.content.Context.MODE_PRIVATE).use {
                            cropped.compress(Bitmap.CompressFormat.JPEG, 88, it)
                        }
                        ctx.getSharedPreferences("jw", android.content.Context.MODE_PRIVATE)
                            .edit().putString("schedule_bg", "schedule_bg.jpg").apply()
                    }
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("保存背景")
            }
        }
    }
}
