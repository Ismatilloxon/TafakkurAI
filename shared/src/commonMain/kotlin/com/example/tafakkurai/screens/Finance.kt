package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.ui.*

@Composable
fun FinanceScreen(onBack: () -> Unit) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        BackBar("Finance", onBack)
        ScrollBody {
            // Net worth hero
            InsetCard(pad = 18) {
                Overline("NET WORTH")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    TfText("$${TFINANCE.net}", color = c.fg, size = 34f, weight = FontWeight.Bold, family = Fonts.mono)
                    Box(Modifier.padding(bottom = 6.dp)) {
                        Pill("+${TFINANCE.change}% · $${TFINANCE.changeAbs}", c.green, c.green.soft(0.16f), leading = { TfIcon("trendingUp", size = 11, color = c.green) })
                    }
                }
            }
            // Allocation
            SectionTitle("Allocation")
            InsetCard {
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    TFINANCE.allocations.forEach { a ->
                        Box(Modifier.weight(a.pct.toFloat()).fillMaxHeight().background(c.named(if (a.color == "blue") "cyan" else a.color)))
                    }
                }
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TFINANCE.allocations.forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(c.named(if (a.color == "blue") "cyan" else a.color)))
                            TfText(a.name, color = c.fg2, size = 13.5f, modifier = Modifier.weight(1f))
                            TfText("$${a.value}", color = c.fg, size = 13f, family = Fonts.mono)
                            TfText("${a.pct}%", color = c.fg3, size = 12f, family = Fonts.mono, modifier = Modifier.width(40.dp), align = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
            }
            // Habits
            SectionTitle("Money habits")
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                TFINANCE.habits.forEach { h ->
                    val statusCol = when (h.status) { "on track", "done" -> c.green; "watch" -> c.warn; else -> c.fg3 }
                    Card(Modifier.fillMaxWidth(), pad = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TfText(h.name, color = c.fg, size = 14f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Pill(h.status, statusCol, statusCol.soft(0.16f))
                        }
                        ProgressBar(h.progress, statusCol, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                    }
                }
            }
            // Insights
            SectionTitle("AI insights")
            ProactiveCard(TFINANCE.insights.first(), modifier = Modifier.padding(horizontal = HPAD.dp))
            Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TFINANCE.insights.drop(1).forEach { ins ->
                    Card(Modifier.fillMaxWidth(), pad = 13) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TfIcon("lineChart", size = 17, color = c.gold)
                            TfText(ins, color = c.fg2, size = 13.5f, lineHeight = 1.45f)
                        }
                    }
                }
            }
            PrivacyFooter("Educational insights only — not financial advice. You set your own risk bands.")
        }
    }
}
