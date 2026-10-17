package com.ncepu.jw.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 全应用排版只钉三档,其余档位沿用 Material3 默认值:
 *  - titleMedium:区块标题 / 课表表头(13sp,SemiBold)
 *  - bodyMedium :正文(13sp)
 *  - labelSmall :极小件 —— 课表时间轴、表头日期、角标(11sp,13sp 行高)
 *
 * 页面里不要再直接写裸 fontSize:课表这类密集场景一律引用这三档,
 * 改字号只改这里,避免各页 8/9/10/11sp 各写一套。
 */
internal val AppTypography = Typography(
    titleMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.3.sp,
    ),
)
