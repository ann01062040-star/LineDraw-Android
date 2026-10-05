package com.linedraw.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linedraw.app.usage.UsageDeclaration

@Composable fun UsageDeclarationScreen(dark: Boolean, opaque: Boolean, error: String,
    onAccept: () -> Unit, onDecline: () -> Unit) {
    BackHandler(onBack = onDecline)
    val colors = if (dark) darkColorScheme(primary = Color(0xFFAAC9FF), onPrimary = Color(0xFF08254D),
        background = Color(0xFF0F1726), onBackground = Color(0xFFF5F7FC),
        surface = Color(0xFF1E2330), onSurface = Color(0xFFF5F7FC))
        else lightColorScheme(primary = Color(0xFF075DD8), onPrimary = Color.White,
            background = Color(0xFFF2F5FC), onBackground = Color(0xFF171A22),
            surface = Color.White, onSurface = Color(0xFF171A22))
    MaterialTheme(colorScheme = colors) {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(if (dark)
            listOf(Color(0xFF111C32), Color(0xFF232038), Color(0xFF101F30)) else
            listOf(Color(0xFFEAF2FF), Color(0xFFF5EFFB), Color(0xFFE9F6FF))))
            .safeDrawingPadding().padding(24.dp).testTag("usageDeclaration"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("LINEDRAW", color = colors.primary, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, fontSize = 13.sp)
            Text("使用聲明", color = colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Surface(Modifier.weight(1f).fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp), color = colors.surface.copy(alpha = if (opaque) 1f else .9f),
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (dark) .15f else 1f))) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    UsageDeclaration.paragraphs.forEach { Text(it, color = colors.onSurface, fontSize = 17.sp, lineHeight = 28.sp) }
                }
            }
            if (error.isNotBlank()) Text(error, color = colors.error)
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("acceptUsage")) {
                Text("同意並繼續")
            }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("declineUsage"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
                border = BorderStroke(1.dp, colors.onBackground.copy(alpha = .5f))) {
                Text("不同意，離開 App")
            }
        }
    }
}
