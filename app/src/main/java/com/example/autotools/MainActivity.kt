package com.example.autotools

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import java.nio.file.WatchEvent

private const val PREFS = "auto_tools"
private const val TARGET_PACKAGE = "target_package"
private const val CLICK_INTERVAL = "click_interval_ms"
private const val CLICK_SEQUENCE = "click_sequence"
private const val DEFAULT_TARGET_PACKAGE = "com.leniu.dpcqln.vivo"
private const val MIN_CLICK_INTERVAL_MS = 500
private const val MAX_CLICK_INTERVAL_MS = 3000
private const val DEFAULT_CLICK_INTERVAL_MS = 1500

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoToolsScreen() }
    }
}

@androidx.compose.runtime.Composable
private fun AutoToolsScreen() {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var targetPackage by remember {
        mutableStateOf(preferences.getString(TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE).orEmpty())
    }
    var clickInterval by remember {
        mutableStateOf(preferences.getInt(CLICK_INTERVAL, DEFAULT_CLICK_INTERVAL_MS).toString())
    }
    var clickSequence by remember { mutableStateOf(preferences.getInt(CLICK_SEQUENCE, 0)) }
    var sequenceMenuExpanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var serviceEnabled by remember { mutableStateOf(false) }

    fun saveSettings() {
        val normalizedInterval = clickInterval.toIntOrNull()
            ?.coerceIn(MIN_CLICK_INTERVAL_MS, MAX_CLICK_INTERVAL_MS)
            ?: DEFAULT_CLICK_INTERVAL_MS
        clickInterval = normalizedInterval.toString()
        preferences.edit {
            putString(TARGET_PACKAGE, targetPackage.trim().ifEmpty { DEFAULT_TARGET_PACKAGE })
                .putInt(CLICK_INTERVAL, normalizedInterval)
                .putInt(CLICK_SEQUENCE, clickSequence)
        }
        status = "目标应用设置已保存"
    }

    fun refreshServiceStatus() {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        serviceEnabled = manager?.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } == true
    }

    LaunchedEffect(Unit) { refreshServiceStatus() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFBFAFF)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "自动下一步",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF081834),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    modifier = Modifier
                        .width(50.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Text("○", modifier = Modifier.padding(12.dp), fontSize = 25.sp, color = Color(0xFF262837))
                }
            }

            InfoCard("授权说明") {
                Text(
                    "在您授权后，自动识别目标游戏中的“下一步”或“继续”按钮并点击。\n建议填写目标应用包名，避免匹配到第三方应用。\n\n请只对您有权操作的应用启用自动化。",
                    color = Color(0xFF4E4E56),
                    fontSize = 16.sp,
                    lineHeight = 20.sp
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        saveSettings()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(30.dp)
                ) { Text("打开无障碍设置", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { openBatterySettings(context) },
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(30.dp)
                ) { Text("打开耗电管理设置", fontSize = 12.sp) }
            }

            InfoCard("配置信息") {
                OutlinedTextField(
                    value = targetPackage,
                    onValueChange = { targetPackage = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("目标应用包名") }
                )

                Spacer(Modifier.height(5.dp))

                OutlinedTextField(
                    value = clickInterval,
                    onValueChange = { clickInterval = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("点击间隔（500 - 3000 毫秒）") }
                )

                Spacer(Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(Color.Transparent),
                    border = BorderStroke(1.dp, Color.DarkGray)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("点击序列", color = Color(0xFF5F5F69), fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { sequenceMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (clickSequence == 0) "下一步 → 快进" else "下一步 → 快进 → 快进")
                        }
                        DropdownMenu(
                            expanded = sequenceMenuExpanded,
                            onDismissRequest = { sequenceMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("下一步 → 快进") },
                                onClick = { clickSequence = 0; sequenceMenuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("下一步 → 快进 → 快进") },
                                onClick = { clickSequence = 1; sequenceMenuExpanded = false }
                            )
                        }
                    }
                }

            }

            Button(
                onClick = { saveSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(34.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B48E8))
            ) {
                Text("保存配置信息", fontSize = 16.sp)
            }

            Text(
                text = status.ifEmpty { if (serviceEnabled) "服务状态：已开启" else "服务状态：未开启，请先打开无障碍设置。" },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                color = Color(0xFF5C5C64),
                fontSize = 16.sp
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun InfoCard(title: String, content: @androidx.compose.runtime.Composable () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (expanded) "⌃" else "⌄", fontSize = 20.sp, color = Color(0xFF55555D))
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

private fun openBatterySettings(context: android.content.Context) {
    try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
            return
        }
    } catch (_: Exception) {
        // Some ROMs block the direct request; use the system list instead.
    }
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}
