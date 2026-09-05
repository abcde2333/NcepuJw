package com.ncepu.jw.data

/**
 * 强智教务系统登录加密算法(逆向自登录页 login() JS)。
 *
 * 流程:
 * 1. POST /Logon.do?method=logon&flag=sess 返回 "scode#sxh"
 *    - scode: 随机串,如 "vhl94t5lyth2eh61401248gOa0W6Z20p134130a8"
 *    - sxh  : 20 位数字串,如 "13223321131331132122"
 * 2. 令 code = userAccount + "%%%" + userPassword
 * 3. 对 code 的前 20 个字符,每个字符后面插入 scode 的前 N 位(N = sxh 的第 i 位),
 *    插入后从 scode 头部移除这 N 位;第 20 位之后的剩余字符直接拼接。
 */
object QiangzhiCrypto {

    fun encodeCredentials(account: String, password: String, precheck: String): String {
        val parts = precheck.split("#")
        require(parts.size >= 2) { "登录预检响应格式异常: $precheck" }
        var scode = parts[0]
        val sxh = parts[1]
        val code = account + "%%%" + password

        val sb = StringBuilder()
        var i = 0
        while (i < code.length) {
            if (i < 20) {
                val n = sxh[i].toString().toInt()
                sb.append(code[i])
                if (n > 0 && scode.isNotEmpty()) {
                    val take = n.coerceAtMost(scode.length)
                    sb.append(scode.take(take))
                    scode = scode.substring(take)
                }
                i++
            } else {
                sb.append(code.substring(i))
                break
            }
        }
        return sb.toString()
    }
}
