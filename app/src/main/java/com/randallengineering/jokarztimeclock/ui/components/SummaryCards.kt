package com.randallengineering.jokarztimeclock.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randallengineering.jokarztimeclock.data.models.PeriodTotals
import com.randallengineering.jokarztimeclock.engine.PayrollEngine
import com.randallengineering.jokarztimeclock.ui.theme.EmeraldSuccess
import com.randallengineering.jokarztimeclock.ui.theme.PurpleAccent
import java.util.Calendar

@Composable
fun SummaryCards(
    totals: PeriodTotals,
    modifier: Modifier = Modifier
) {
    val todayStats = totals.todayStats

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // Today Summary Card
        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PurpleAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = todayStats?.type?.uppercase() ?: "SALARY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurpleAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = PayrollEngine.formatMoney(totals.todayEarnings),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                    if (totals.todayOtEarnings > 0.0) {
                        Text(
                            text = " +${PayrollEngine.formatMoney(totals.todayOtEarnings)} OT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurpleAccent,
                            modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                        )
                    }
                }

                Text(
                    text = "${String.format("%.1f", todayStats?.payableHours ?: 0.0)}h Paid",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TECH CLOCKED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PayrollEngine.formatDurationShort(todayStats?.clockedMs ?: 0L),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Pay Period Summary Card
        val dStart = Calendar.getInstance().apply { timeInMillis = totals.startOfPeriod }
        val dEnd = Calendar.getInstance().apply { timeInMillis = totals.endOfPeriod }
        val periodLabel = "${dStart.get(Calendar.MONTH) + 1}/${dStart.get(Calendar.DAY_OF_MONTH)} - ${dEnd.get(Calendar.MONTH) + 1}/${dEnd.get(Calendar.DAY_OF_MONTH)}"

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "PAY PERIOD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldSuccess.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = periodLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = PayrollEngine.formatMoney(totals.periodEarnings),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }

                val ptoStr = if (totals.totalPtoHoursPeriod > 0.0) " (${String.format("%.1f", totals.totalPtoHoursPeriod)}h PTO)" else ""
                Text(
                    text = "${String.format("%.1f", totals.totalPayableHoursPeriod)}h Paid$ptoStr",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "TECH CLOCKED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PayrollEngine.formatDurationShort(totals.totalClockedMsPeriod),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
