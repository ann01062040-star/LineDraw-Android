package com.linedraw.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var legalDocument by remember { mutableStateOf<LegalDocument?>(null) }
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
            Text("LineDraw 獨立版", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(UsageDeclaration.TITLE, color = colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Surface(Modifier.weight(1f).fillMaxWidth().shadow(12.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp), color = colors.surface.copy(alpha = if (opaque) 1f else .9f),
                contentColor = colors.onSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (dark) .15f else 1f))) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    UsageDeclaration.sections.forEach { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(section.title, color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(section.body, color = colors.onSurface, fontSize = 17.sp, lineHeight = 28.sp)
                        }
                    }
                    TextButton(onClick = { legalDocument = LegalDocument.GUIDE }, modifier = Modifier.testTag("consentGuide")) { Text("閱讀使用教學") }
                    TextButton(onClick = { legalDocument = LegalDocument.LICENSE }, modifier = Modifier.testTag("consentLicense")) { Text("閱讀完整授權") }
                    TextButton(onClick = { legalDocument = LegalDocument.PRIVACY }, modifier = Modifier.testTag("consentPrivacy")) { Text("閱讀隱私說明") }
                }
            }
            if (error.isNotBlank()) Text(error, color = colors.error)
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("acceptUsage")) {
                Text("我已了解，開始使用")
            }
            OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("declineUsage"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBackground),
                border = BorderStroke(1.dp, colors.onBackground.copy(alpha = .5f))) {
                Text("離開 App")
            }
        }
        legalDocument?.let { LegalDocumentDialog(it) { legalDocument = null } }
    }
}
