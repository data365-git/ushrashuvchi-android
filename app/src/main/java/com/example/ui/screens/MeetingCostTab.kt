package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiPricing
import com.example.data.model.AiCallLog
import com.example.ui.viewmodel.AppViewModel

@Composable
fun MeetingCostTab(
    meetingId: Int,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val calls by viewModel.meetingCostBreakdown(meetingId).collectAsState(initial = emptyList())
    val exchangeRate by viewModel.exchangeRateUzs.collectAsState()

    val totalMicros = calls.sumOf { it.costUsdMicros }
    val totalLatencyMs = calls.sumOf { it.latencyMs }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "AI usage breakdown",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${calls.size} AI call(s) · ${totalLatencyMs / 1000}s total processing",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        if (calls.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No AI calls yet",
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Generate AI summary to see cost breakdown",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(calls) { call ->
                    CostRow(call, exchangeRate)
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Total", fontWeight = FontWeight.Bold)
                            Text(
                                "${calls.size} calls · ${totalLatencyMs / 1000}s",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                formatUsd(totalMicros),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                formatUzs(totalMicros, exchangeRate),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostRow(call: AiCallLog, exchangeRate: Double) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    call.kind,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatUsd(call.costUsdMicros),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                call.model,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            val tokenSummary = buildString {
                if (call.audioTokens > 0) append("Audio ${call.audioTokens} · ")
                append("In ${call.promptTokens} · Out ${call.responseTokens}")
            }
            Text(
                tokenSummary,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row {
                Text(
                    "${call.latencyMs / 1000.0}s",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatUzs(call.costUsdMicros, exchangeRate),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            call.errKind?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Failed: $err",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatUsd(micros: Long): String {
    val usd = GeminiPricing.microsToUsd(micros)
    return "$${"%.4f".format(usd)}"
}

private fun formatUzs(micros: Long, rate: Double): String {
    val uzs = GeminiPricing.microsToUzs(micros, rate)
    return "≈${"%,.0f".format(uzs)} UZS"
}
