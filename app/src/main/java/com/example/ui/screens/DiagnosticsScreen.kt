package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiCallLog
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentCalls by viewModel.loadRecentAiCalls().collectAsState(initial = emptyList())
    val aiHealthStatus by viewModel.aiHealthStatus.collectAsState()
    val lastAiError by viewModel.lastAiError.collectAsState()
    val sttModel by viewModel.sttModel.collectAsState()
    val llmModel by viewModel.llmModel.collectAsState()
    val customKey by viewModel.customGeminiKey.collectAsState()

    var testResult by remember { mutableStateOf<String?>(null) }
    var modelResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // API Key section
            item {
                DiagCard(title = "API Key") {
                    val keyDisplay = if (customKey.length > 4)
                        "•".repeat(customKey.length - 4) + customKey.takeLast(4)
                    else "Not configured"
                    Text("Key: $keyDisplay", fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    testResult?.let {
                        Text(it, fontSize = 13.sp,
                            color = if (it.startsWith("✓")) Color(0xFF166534) else Color(0xFFB91C1C))
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(onClick = {
                        testResult = "Testing…"
                        viewModel.testApiKey { ok, msg -> testResult = msg }
                    }) { Text("Test key now") }
                }
            }

            // Selected models
            item {
                DiagCard(title = "Selected Models") {
                    Text("STT:  $sttModel", fontSize = 13.sp)
                    Text("Chat: $llmModel", fontSize = 13.sp)
                }
            }

            // Model health test
            item {
                DiagCard(title = "Model Health") {
                    Button(onClick = {
                        modelResults = emptyMap()
                        viewModel.testAllModels { model, status ->
                            modelResults = modelResults + (model to status)
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Test all models")
                    }
                    Spacer(Modifier.height(8.dp))
                    modelResults.forEach { (model, status) ->
                        val isOk = status.startsWith("ok:")
                        val latency = if (isOk) status.removePrefix("ok:").toLongOrNull() else null
                        val errInfo = if (!isOk) status.removePrefix("err:") else null
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isOk) "✓" else "✗",
                                color = if (isOk) Color(0xFF166534) else Color(0xFFB91C1C),
                                fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                            Text(model, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text(if (isOk) "${latency}ms" else errInfo ?: "error",
                                fontSize = 12.sp,
                                color = if (isOk) Color(0xFF475569) else Color(0xFFB91C1C))
                        }
                    }
                }
            }

            // AI health summary
            item {
                DiagCard(title = "AI Session Health") {
                    Text(
                        when (aiHealthStatus) {
                            AppViewModel.AiHealthStatus.OK -> "✓ Last call succeeded"
                            AppViewModel.AiHealthStatus.QUOTA -> "⚠ Quota exhausted"
                            AppViewModel.AiHealthStatus.BAD_KEY -> "✗ API key rejected"
                            AppViewModel.AiHealthStatus.ERROR -> "✗ Last call failed"
                            AppViewModel.AiHealthStatus.UNKNOWN -> "· No calls yet this session"
                        },
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                    )
                    lastAiError?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text("Last error: ${err.kind.name}", fontSize = 12.sp, color = Color(0xFFB91C1C))
                        Text(err.suggestion, fontSize = 12.sp, color = Color(0xFF475569))
                    }
                }
            }

            // Recent AI calls
            item {
                DiagCard(title = "Recent AI Calls (last 20)") {
                    if (recentCalls.isEmpty()) {
                        Text("No calls logged yet.", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
            }
            items(recentCalls, key = { it.id }) { call ->
                AiCallRow(call)
            }

            item {
                DiagCard("Storage & Files") {
                    var rescanDone by remember { mutableStateOf(false) }
                    Text(
                        "Scan the recordings folder for audio files that don't have a " +
                        "matching database entry and rebuild missing records.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.rescanRecordings()
                                    rescanDone = true
                                }
                            },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Rescan recordings")
                        }
                        if (rescanDone) {
                            Spacer(Modifier.width(12.dp))
                            Text("✓ Done", color = Color(0xFF166534), fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                DiagCard("Trash") {
                    var trashInfo by remember { mutableStateOf<String?>(null) }
                    var cleared by remember { mutableStateOf(false) }
                    Text(
                        "Cancelled recordings sit here for 7 days, then are auto-deleted.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val trashDir = java.io.File(context.getExternalFilesDir(null), "Recordings/.trash")
                            val files = trashDir.listFiles() ?: emptyArray()
                            val totalKb = files.sumOf { it.length() } / 1024
                            trashInfo = "${files.size} file(s), ${totalKb} KB"
                        }) { Text("Check trash") }
                        OutlinedButton(onClick = {
                            val trashDir = java.io.File(context.getExternalFilesDir(null), "Recordings/.trash")
                            trashDir.listFiles()?.forEach { it.delete() }
                            cleared = true
                            trashInfo = "0 file(s), 0 KB"
                        }) { Text("Empty trash") }
                    }
                    trashInfo?.let { info ->
                        Text(info, style = MaterialTheme.typography.bodySmall)
                    }
                    if (cleared) {
                        Text("✓ Trash emptied", color = Color(0xFF166534), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun AiCallRow(call: AiCallLog) {
    val isOk = call.errKind == null
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isOk) "✓" else "✗",
                color = if (isOk) Color(0xFF166534) else Color(0xFFB91C1C),
                fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${call.kind}  ·  ${call.model}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(buildString {
                    if (!isOk) append("${call.errKind}  ")
                    if (call.httpCode != null) append("HTTP ${call.httpCode}  ")
                    append("${call.latencyMs}ms")
                }, fontSize = 11.sp, color = Color(0xFF64748B))
            }
        }
    }
}
