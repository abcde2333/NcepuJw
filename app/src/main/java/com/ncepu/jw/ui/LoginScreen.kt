package com.ncepu.jw.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.Capsule
import com.ncepu.jw.R

/** 登录方式:教务密码(强智直登)/ 统一认证(OAuth2 协议直登 + 网页降级) */
private enum class LoginMode(val label: String, val icon: ImageVector) {
    JWXT("教务密码", Icons.Filled.Lock),
    SSO("统一认证", Icons.Filled.VerifiedUser),
}

@Composable
fun LoginScreen(
    account: String,
    password: String,
    loading: Boolean,
    error: String?,
    onAccountChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onSkip: () -> Unit = {},
    onSsoLogin: () -> Unit = {},
    onSsoWebLogin: () -> Unit = {},
    ssoOnly: Boolean = false,
    onModeChange: (Boolean) -> Unit = {},   // 参数:是否切到"统一认证"(用于回填各模式独立存的凭据)
) {
    // 校外模式:仅统一认证登录(建隧道),隐藏教务密码方式
    var mode by remember(ssoOnly) {
        mutableStateOf(if (ssoOnly) LoginMode.SSO else LoginMode.JWXT)
    }
    androidx.compose.runtime.LaunchedEffect(ssoOnly) { if (ssoOnly) mode = LoginMode.SSO }
    // 进入登录页时按当前模式回填已存凭据(避免共用输入框串密码)
    androidx.compose.runtime.LaunchedEffect(Unit) { onModeChange(mode == LoginMode.SSO) }
    var showPassword by remember { mutableStateOf(false) }

    // 液态玻璃折射源:品牌渐变 + 大校徽水印
    val backdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // 折射源层(仅进 backdrop 采样,视觉上就是登录页自己的底色)
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        )
                    )
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.ncepu_logo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(300.dp)
                    .alpha(0.07f),
                contentScale = ContentScale.Fit,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 380f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ncepu_logo),
                contentDescription = "华北电力大学校徽",
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("华电教务", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text("jwxt.ncepu.edu.cn", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            // ---- 登录方式切换(液态玻璃胶囊,同悬浮底栏材质) ----
            if (!ssoOnly) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoginMode.entries.forEach { m ->
                        GlassModePill(
                            mode = m,
                            selected = mode == m,
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f),
                        ) { mode = m; onModeChange(m == LoginMode.SSO) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = account,
                onValueChange = onAccountChange,
                label = { Text("学号") },
                singleLine = true,
                enabled = !loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = {
                    Text(
                        if (mode == LoginMode.JWXT) "教务系统密码"
                        else "统一身份认证密码"
                    )
                },
                singleLine = true,
                enabled = !loading,
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff,
                            contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { if (mode == LoginMode.JWXT) onLogin() else onSsoLogin() },
                enabled = !loading && account.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    AnimatedContent(
                        targetState = mode,
                        label = "loginButton",
                    ) { m ->
                        Text(if (m == LoginMode.JWXT) "登 录" else "统一认证登录")
                    }
                }
            }

            // 统一认证模式下的降级入口(仅校内直连时;校外模式走隧道+短信,不开 WebView)
            AnimatedVisibility(
                visible = mode == LoginMode.SSO && !ssoOnly,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                TextButton(onClick = onSsoWebLogin, enabled = !loading) {
                    Text("需要验证码/扫码?打开统一认证网页", fontSize = 11.sp)
                }
            }

            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = mode,
                label = "loginHint",
            ) { m ->
                Text(
                    if (m == LoginMode.JWXT)
                        "使用教务系统(强智)账号密码登录\n与 https://jwxt.ncepu.edu.cn 网页端一致"
                    else
                        "使用统一身份认证账号密码登录(密码可能与教务密码不同)\n协议直登;验证码/多因素场景请用下方网页入口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/** 液态玻璃模式胶囊(同悬浮底栏材质:vibrancy + blur + lens) */
@Composable
private fun GlassModePill(
    mode: LoginMode,
    selected: Boolean,
    backdrop: com.kyant.backdrop.Backdrop,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "pillFill",
    )
    val containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f * fill)
    val idleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier
            .height(46.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx())
                    lens(14f.dp.toPx(), 14f.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.55f) },
                onDrawSurface = {
                    drawRect(idleColor)
                    drawRect(containerColor)
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
            ) { onSelect() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                mode.icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = contentColor,
            )
            Spacer(Modifier.size(5.dp))
            Text(
                mode.label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}
