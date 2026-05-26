package chat.simplex.common.views.chat.item

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.views.chat.PaymentLimits
import chat.simplex.common.views.wallet.*

// ── Palette ──────────────────────────────────────────────────────
private val CardDark1 = Color(0xFF1A1F36)
private val CardDark2 = Color(0xFF0D1025)
private val AccentBlue = Color(0xFF6C8EFF)
private val AccentCyan = Color(0xFF38BDF8)
private val AccentGreen = Color(0xFF34D399)
private val AccentAmber = Color(0xFFFBBF24)
private val AccentRed = Color(0xFFF87171)
private val TextWhite = Color(0xFFF1F5F9)
private val TextDim = Color(0xFF94A3B8)
private val ChipBg = Color(0x33FFFFFF)

private val PaidGrad1 = Color(0xFF064E3B)
private val PaidGrad2 = Color(0xFF022C22)
private val ConfirmGrad1 = Color(0xFF065F46)
private val ConfirmGrad2 = Color(0xFF022C22)

// ═════════════════════════════════════════════════════════════════
//  Invoice card (sender = "Payment Request", receiver = "Invoice")
// ═════════════════════════════════════════════════════════════════

@Composable
fun CIPaymentInvoiceView(
    invoice: PaymentInvoice,
    isSent: Boolean,
    onPayClicked: (PaymentInvoice) -> Unit
) {
    val isPaid = invoice.status == PaymentInvoiceStatus.PAID
    val isExpired = invoice.status == PaymentInvoiceStatus.EXPIRED
    val isCancelled = invoice.status == PaymentInvoiceStatus.CANCELLED

    val bgBrush = when {
        isPaid -> Brush.linearGradient(
            colors = listOf(PaidGrad1, PaidGrad2),
            start = Offset(0f, 0f), end = Offset(600f, 600f)
        )
        else -> Brush.linearGradient(
            colors = listOf(CardDark1, CardDark2),
            start = Offset(0f, 0f), end = Offset(600f, 600f)
        )
    }

    val accentColor = when {
        isPaid -> AccentGreen
        isExpired || isCancelled -> AccentRed
        else -> AccentBlue
    }

    Box(
        Modifier
            .width(270.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
    ) {
        // Decorative circles (credit-card style)
        Box(
            Modifier
                .size(90.dp)
                .offset(x = 210.dp, y = (-20).dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.08f))
        )
        Box(
            Modifier
                .size(60.dp)
                .offset(x = 230.dp, y = 50.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.06f))
        )

        Column(Modifier.padding(16.dp)) {
            // ── Header row ──────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Network chip
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ChipBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        invoice.network.symbol,
                        style = MaterialTheme.typography.caption.copy(
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isSent) "PAYMENT REQUEST" else "INVOICE",
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = TextDim,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                )
                Spacer(Modifier.weight(1f))
                StatusBadge(invoice.status)
            }

            Spacer(Modifier.height(16.dp))

            // ── Amount ──────────────────────────────────────────
            Text(
                invoice.amount,
                style = MaterialTheme.typography.h4.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    shadow = Shadow(color = accentColor.copy(alpha = 0.3f), offset = Offset(0f, 2f), blurRadius = 8f)
                )
            )
            Text(
                invoice.tokenSymbol,
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    letterSpacing = 0.5.sp
                )
            )

            Spacer(Modifier.height(14.dp))

            // ── Details ─────────────────────────────────────────
            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            DetailRow("Network", invoice.network.displayName)
            DetailRow("To", invoice.toAddress.take(8) + "..." + invoice.toAddress.takeLast(6))

            if (!invoice.memo.isNullOrBlank()) {
                DetailRow("Memo", invoice.memo)
            }

            if (invoice.txHash != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TX: ${invoice.txHash.take(10)}...${invoice.txHash.takeLast(6)}",
                        style = MaterialTheme.typography.caption.copy(
                            color = AccentGreen,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Pay button (receiver only, pending only, not expired) ─
            val expired = PaymentLimits.isInvoiceExpired(invoice)
            if (!isSent && invoice.status == PaymentInvoiceStatus.PENDING && !expired) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onPayClicked(invoice) },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(AccentBlue, AccentCyan)),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Pay Now",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Payment confirmation card
// ═════════════════════════════════════════════════════════════════

@Composable
fun CIPaymentConfirmView(confirmation: PaymentConfirmation) {
    Box(
        Modifier
            .width(270.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(ConfirmGrad1, ConfirmGrad2),
                    start = Offset(0f, 0f), end = Offset(600f, 600f)
                )
            )
    ) {
        // Decorative
        Box(
            Modifier
                .size(70.dp)
                .offset(x = 220.dp, y = (-10).dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.12f))
        )

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = AccentGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "PAYMENT SENT",
                    style = MaterialTheme.typography.caption.copy(
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            Text(
                "Transaction",
                style = MaterialTheme.typography.caption.copy(color = TextDim, fontSize = 10.sp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                confirmation.txHash.take(16) + "..." + confirmation.txHash.takeLast(8),
                style = MaterialTheme.typography.body2.copy(
                    color = TextWhite.copy(alpha = 0.85f),
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════
//  Helpers
// ═════════════════════════════════════════════════════════════════

@Composable
private fun StatusBadge(status: PaymentInvoiceStatus) {
    val (label, bg, fg) = when (status) {
        PaymentInvoiceStatus.PENDING -> Triple("PENDING", AccentAmber.copy(alpha = 0.15f), AccentAmber)
        PaymentInvoiceStatus.PAID -> Triple("PAID", AccentGreen.copy(alpha = 0.15f), AccentGreen)
        PaymentInvoiceStatus.EXPIRED -> Triple("EXPIRED", AccentRed.copy(alpha = 0.15f), AccentRed)
        PaymentInvoiceStatus.CANCELLED -> Triple("CANCELLED", Color.Gray.copy(alpha = 0.15f), Color.Gray)
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.caption.copy(
                fontWeight = FontWeight.Bold,
                color = fg,
                fontSize = 9.sp,
                letterSpacing = 0.8.sp
            )
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.caption.copy(color = TextDim, fontSize = 11.sp)
        )
        Text(
            value,
            style = MaterialTheme.typography.caption.copy(
                color = TextWhite.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp)
        )
    }
}
