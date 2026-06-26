package com.autoclicker.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoClickerTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到界面时刷新状态
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var accessibilityEnabled by remember { mutableStateOf(ClickAccessibilityService.isRunning) }
    var overlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(LocalContext.current)) }
    var floatingActive by remember { mutableStateOf(FloatingControlService.instance != null) }

    // 定时刷新状态
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            accessibilityEnabled = ClickAccessibilityService.isRunning
            overlayEnabled = Settings.canDrawOverlays(LocalContext.current)
            floatingActive = FloatingControlService.instance != null
        }
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连点器", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFFF0F0F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 权限状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("权限状态", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionRow(
                        title = "无障碍服务",
                        icon = Icons.Default.Accessibility,
                        enabled = accessibilityEnabled,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )

                    PermissionRow(
                        title = "悬浮窗权限",
                        icon = Icons.Default.Layers,
                        enabled = overlayEnabled,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"))
                            )
                        }
                    )
                }
            }

            // 控制区
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("控制", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!overlayEnabled) {
                                Toast.makeText(context, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!accessibilityEnabled) {
                                Toast.makeText(context, "请先开启无障碍服务", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (floatingActive) {
                                context.stopService(Intent(context, FloatingControlService::class.java))
                                floatingActive = false
                            } else {
                                context.startForegroundService(Intent(context, FloatingControlService::class.java))
                                floatingActive = true
                                // 最小化到后台
                                (context as? ComponentActivity)?.moveTaskToBack(true)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (floatingActive) Color(0xFFF44336) else Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (floatingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (floatingActive) "关闭悬浮窗" else "打开悬浮窗",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!accessibilityEnabled || !overlayEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFF3E0))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "需要先开启两项权限才能使用",
                                color = Color(0xFFE65100),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 使用说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF6)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("使用说明", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. 开启无障碍服务和悬浮窗权限", fontSize = 14.sp, color = Color(0xFF555555))
                    Text("2. 点击「打开悬浮窗」，出现控制条", fontSize = 14.sp, color = Color(0xFF555555))
                    Text("3. 切到目标 App，点 ◎ 选取点击位置", fontSize = 14.sp, color = Color(0xFF555555))
                    Text("4. 用 +/- 调整点击速度", fontSize = 14.sp, color = Color(0xFF555555))
                    Text("5. 点 ▶ 开始，■ 停止", fontSize = 14.sp, color = Color(0xFF555555))
                    Text("6. 拖拽控制条可移动位置", fontSize = 14.sp, color = Color(0xFF555555))
                }
            }
        }
    }
}

@Composable
fun PermissionRow(title: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) Color(0xFF4CAF50) else Color(0xFFFF9800)
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                if (enabled) "已开启" else "去开启",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun AutoClickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1A1A2E),
            onPrimary = Color.White,
            background = Color(0xFFF0F0F5),
            surface = Color.White
        ),
        content = content
    )
}
