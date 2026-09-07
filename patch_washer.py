import io

# 1) UjingClient:payPrice 按元格式化(带 ¥ 前缀)
p = "app/src/main/java/com/ncepu/jw/data/UjingClient.kt"
s = io.open(p, encoding="utf-8").read()
old = """        /** 从订单详情(data 层)提取展示字段 */
        fun parseOrder(detail: JSONObject?): WasherOrderInfo {
            if (detail == null) return WasherOrderInfo("", "", "", "", "", 0)
            val fen = detail.optInt("payPrice", -1)
            val pay = if (fen >= 0) fen2yuan(fen) else detail.optString("payPrice", "")"""
new = """        /**
         * 从订单详情(data 层)提取展示字段。
         * payPrice:服务端给的是“元”(数值或字符串,如 2.1),格式化为 ¥x.xx(对齐参考实现)。
         */
        fun parseOrder(detail: JSONObject?): WasherOrderInfo {
            if (detail == null) return WasherOrderInfo("", "", "", "", "", 0)
            val rawPay = detail.opt("payPrice")"""
assert old in s, "parseOrder head"
s = s.replace(old, new, 1)
old = """            return WasherOrderInfo(
                orderId = detail.str("orderId").ifBlank { detail.str("orderNo") },
                deviceNo = detail.str("deviceNo"),
                status = detail.str("status"),
                statusText = detail.str("statusRemark").ifBlank { statusText(detail.str("status")) },
                payPrice = pay,
                remainTimeSeconds = detail.optInt("remainTime", 0),
            )
        }"""
new = """            val payText = when (rawPay) {
                null -> ""
                is Number -> "¥" + String.format(java.util.Locale.US, "%.2f", rawPay.toDouble())
                else -> {
                    val t = rawPay.toString().trim()
                    val d = t.toDoubleOrNull()
                    if (t.isEmpty()) "" else if (d != null) "¥" + String.format(java.util.Locale.US, "%.2f", d) else t
                }
            }
            return WasherOrderInfo(
                orderId = detail.str("orderId").ifBlank { detail.str("orderNo") },
                deviceNo = detail.str("deviceNo"),
                status = detail.str("status"),
                statusText = detail.str("statusRemark").ifBlank { statusText(detail.str("status")) },
                payPrice = payText,
                remainTimeSeconds = detail.optInt("remainTime", 0),
            )
        }"""
assert old in s, "parseOrder tail"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("UjingClient ok")

# 2) WasherScreen
p = "app/src/main/java/com/ncepu/jw/ui/WasherScreen.kt"
s = io.open(p, encoding="utf-8").read()
old = "    val selectedAdditions: Map<String, Int?> = emptyMap(), // 加购组 key → 档位 id(null=不添加)"
new = old + "\n    val autoStartAfterPay: Boolean = false,               // 支付成功后自动启动洗衣机"
assert old in s
s = s.replace(old, new, 1)
old = """    onStartWash: () -> Unit,
    onRemoveWasher: (String) -> Unit = {},"""
new = """    onStartWash: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit = {},
    onRemoveWasher: (String) -> Unit = {},"""
assert old in s
s = s.replace(old, new, 1)
old = """                                    if (order.payPrice.isNotBlank()) {
                                        Text("金额:¥${order.payPrice}", fontSize = 13.sp)
                                    }"""
new = """                                    if (order.payPrice.isNotBlank()) {
                                        Text("金额:${order.payPrice}", fontSize = 13.sp)
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "支付成功后自动启动洗衣机",
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        androidx.compose.material3.Switch(
                                            checked = state.autoStartAfterPay,
                                            onCheckedChange = onAutoStartChange,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))"""
assert old in s
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("WasherScreen ok")

# 3) MainActivity
p = "app/src/main/java/com/ncepu/jw/MainActivity.kt"
s = io.open(p, encoding="utf-8").read()
old = """    fun washerPay() {
        val order = washerState.currentOrder ?: return
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.paymentArguments(washerToken, order.orderId)
            val payInfo = r.json?.optJSONObject("payInfo")
            val orderInfo = payInfo?.optString("orderInfo", "") ?: ""
            if (r.ok && orderInfo.isNotBlank()) {
                washerState = washerState.copy(loading = false, payUrl = "已生成支付宝参数")
                openAlipay(orderInfo)
            } else {
                val h5 = payInfo?.optString("h5_url", "") ?: ""
                washerState = washerState.copy(
                    loading = false,
                    message = if (h5.isNotBlank()) "请用浏览器打开 H5 支付链接完成支付" else "支付参数失败:" + UjingClient.readable(r.code, r.msg),
                )
            }
        }
    }"""
new = """    fun washerPay(activity: android.app.Activity) {
        val order = washerState.currentOrder ?: return
        washerState = washerState.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = ujing.paymentArguments(washerToken, order.orderId)
            val payInfo = r.json?.optJSONObject("payInfo")
            val orderInfo = payInfo?.optString("orderInfo", "") ?: ""
            if (!r.ok || orderInfo.isBlank()) {
                washerState = washerState.copy(
                    loading = false,
                    message = "支付参数获取失败:" + UjingClient.readable(r.code, r.msg),
                )
                return@launch
            }
            // PayTask 阻塞调用,需在后台线程;orderInfo 是支付宝 SDK 签名订单串
            val result = withContext(Dispatchers.IO) {
                com.alipay.sdk.app.PayTask(activity).payV2(orderInfo, true)
            }
            val paid = result.contains("resultStatus={9000}")
            val refreshed = ujing.orderDetail(washerToken, order.orderId)
            washerState = washerState.copy(
                loading = false,
                currentOrder = UjingClient.Parsers.parseOrder(refreshed.json),
                message = if (paid) {
                    if (washerState.autoStartAfterPay) "支付成功,3 秒后自动启动洗衣机" else "支付成功"
                } else "支付未完成,可在订单中重试",
            )
            if (paid && washerState.autoStartAfterPay) {
                kotlinx.coroutines.delay(3000)
                ujing.startOrder(washerToken, order.orderId)
                val d2 = ujing.orderDetail(washerToken, order.orderId)
                washerState = washerState.copy(currentOrder = UjingClient.Parsers.parseOrder(d2.json))
            }
        }
    }"""
assert old in s, "washerPay not found"
s = s.replace(old, new, 1)

old = """    private fun openAlipay(orderInfo: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("alipays://platformapi/startapp?saId=10000007&orderSuffix=" +
                java.net.URLEncoder.encode(orderInfo, "UTF-8"))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<android.app.Application>().startActivity(intent)
        } catch (_: Exception) {
        }
    }

"""
assert old in s, "openAlipay not found"
s = s.replace(old, "", 1)

old = "                                onPay = { vm.washerPay() },"
new = """                                onPay = { vm.washerPay(this@MainActivity) },
                                onAutoStartChange = { vm.washerState = vm.washerState.copy(autoStartAfterPay = it) },"""
assert old in s, "onPay call site"
s = s.replace(old, new, 1)
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("MainActivity ok")
