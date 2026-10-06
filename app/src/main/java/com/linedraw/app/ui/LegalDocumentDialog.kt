package com.linedraw.app.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linedraw.app.usage.UsageDeclaration

enum class LegalDocument(val title: String) {
    USAGE("使用聲明"), PRIVACY("隱私說明"), LICENSE("完整授權")
}

@Composable
fun LegalDocumentDialog(document: LegalDocument, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val content = remember(document, context) {
        when (document) {
            LegalDocument.USAGE -> UsageDeclaration.paragraphs.joinToString("\n\n")
            LegalDocument.PRIVACY -> context.assets.open("PRIVACY.txt").bufferedReader().use { it.readText() }
            LegalDocument.LICENSE -> listOf("NOTICE.txt", "LICENSE.txt").joinToString("\n\n") { name ->
                context.assets.open(name).bufferedReader().use { it.readText() }
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("legalDocument"),
        title = { Text(document.title) },
        text = { Text(content, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), fontSize = 15.sp, lineHeight = 23.sp) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("closeLegalDocument")) { Text("關閉") } })
}
