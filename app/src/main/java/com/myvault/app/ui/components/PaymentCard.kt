package com.myvault.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** The colours of one card stock: the plastic itself, the light on it, and the embossed ink. */
private class CardFinish(
    val base: List<Color>,
    /** Coloured light pooling in the lower right corner. */
    val glow: Color,
    /** Top-to-bottom gradient of the raised lettering. */
    val ink: List<Color>,
    /** Small printed captions such as "VALID THRU". */
    val caption: Color,
)

/** Black metal with gold lettering, for credit cards. */
private val Obsidian = CardFinish(
    base = listOf(Color(0xFF3B3E49), Color(0xFF1A1C23), Color(0xFF08090C)),
    glow = Color(0xFFC9A44C),
    ink = listOf(Color(0xFFFFF4CC), Color(0xFFD6AE52)),
    caption = Color(0xFFC2AB70),
)

/** Deep blue with silver lettering, for debit cards. */
private val Sapphire = CardFinish(
    base = listOf(Color(0xFF3272FF), Color(0xFF1741BD), Color(0xFF09195E)),
    glow = Color(0xFF63CBFF),
    ink = listOf(Color(0xFFFFFFFF), Color(0xFFCAD6F0)),
    caption = Color(0xFFBCCBF6),
)

/** Width of the card in design units; every measurement below is in these units. */
private const val DESIGN_WIDTH = 340f

/** ISO/IEC 7810 ID-1, the size of every bank card. */
private const val CARD_ASPECT = 85.6f / 53.98f

/**
 * A payment card drawn to look like the physical thing: card stock with an engraved pattern and
 * a band of reflected light, a gold contact chip, the contactless mark and raised lettering.
 *
 * It shows only what MyVault stores. The full number is never kept, so all but the last four
 * digits are always dots. With [placeholders] the empty parts show a hint of what goes there,
 * which is what the add form wants; without it they are left off.
 */
@Composable
fun CardPreview(
    nickname: String,
    network: String,
    lastFour: String,
    modifier: Modifier = Modifier,
    holder: String = "",
    bank: String = "",
    expiry: String = "",
    credit: Boolean = true,
    placeholders: Boolean = false,
) {
    val finish = if (credit) Obsidian else Sapphire
    val shape = RoundedCornerShape(percent = 7)
    val measurer = rememberTextMeasurer()
    val kind = if (credit) "Credit card" else "Debit card"
    val spoken = listOf(nickname, bank, network, kind, lastFour.takeIf { it.isNotBlank() }?.let { "ending in $it" }.orEmpty())
        .filter { it.isNotBlank() }
        .joinToString(", ")

    Canvas(
        modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp)
            .aspectRatio(CARD_ASPECT)
            .shadow(20.dp, shape, ambientColor = Color(0x99000A1F), spotColor = Color(0xCC000A1F))
            .clip(shape)
            .testTag("payment-card")
            .semantics { contentDescription = spoken },
    ) {
        val u = size.width / DESIGN_WIDTH
        drawCardStock(finish, u)
        drawChip(Offset(26f * u, 64f * u), u)
        drawContactless(Offset(86f * u, 82f * u), u)

        // Top row: the issuer on the left, the product name on the right.
        val issuer = bank.ifBlank { nickname }.ifBlank { if (placeholders) "Bank name" else kind }
        val issuerFaded = bank.isBlank() && nickname.isBlank()
        val issuerStyle = TextStyle(fontSize = (15f * u).toSp(), fontWeight = FontWeight.SemiBold, letterSpacing = (0.2f * u).toSp())
        val issuerText = measurer.line(issuer, issuerStyle, 180f * u)
        drawText(issuerText, Color.White.copy(alpha = if (issuerFaded) 0.5f else 0.95f), Offset(26f * u, 22f * u))

        val product = if (bank.isNotBlank() && nickname.isNotBlank()) nickname else kind
        val productStyle = TextStyle(fontSize = (9.5f * u).toSp(), fontWeight = FontWeight.Medium, letterSpacing = (1.4f * u).toSp())
        val productText = measurer.line(product.uppercase(), productStyle, 104f * u)
        drawText(productText, finish.caption, Offset(size.width - 26f * u - productText.size.width, 26f * u))

        // The number, raised like embossing. Only the last four digits are ever real.
        val digits = lastFour.ifBlank { "••••" }
        val numberStyle = TextStyle(fontSize = (19f * u).toSp(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = (0.8f * u).toSp())
        embossed(measurer.line("••••  ••••  ••••  $digits", numberStyle, size.width - 52f * u), Offset(26f * u, 116f * u), finish, u)

        val captionStyle = TextStyle(fontSize = (6.8f * u).toSp(), fontWeight = FontWeight.Medium, letterSpacing = (1.1f * u).toSp())
        val valueStyle = TextStyle(fontSize = (12.5f * u).toSp(), fontWeight = FontWeight.SemiBold, letterSpacing = (0.9f * u).toSp())

        if (holder.isNotBlank() || placeholders) {
            drawText(measurer.line("CARD HOLDER", captionStyle, 130f * u), finish.caption, Offset(26f * u, 163f * u))
            val name = measurer.line(holder.ifBlank { "Your name" }.uppercase(), valueStyle, 116f * u)
            embossed(name, Offset(26f * u, 174f * u), finish, u, alpha = if (holder.isBlank()) 0.45f else 1f)
        }
        if (expiry.isNotBlank() || placeholders) {
            drawText(measurer.line("VALID THRU", captionStyle, 60f * u), finish.caption, Offset(152f * u, 163f * u))
            val date = measurer.line(expiry.ifBlank { "MM/YY" }, valueStyle.copy(fontFamily = FontFamily.Monospace), 60f * u)
            embossed(date, Offset(152f * u, 174f * u), finish, u, alpha = if (expiry.isBlank()) 0.45f else 1f)
        }

        // Card network, bottom right, as a wordmark. Long names shrink to fit rather than being cut.
        if (network.isNotBlank()) {
            val room = 104f * u
            val name = network.trim().uppercase()
            val mark = listOf(21f, 19f, 17f, 15.5f, 14f, 12.5f)
                .map { size -> TextStyle(fontSize = (size * u).toSp(), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = (0.3f * u).toSp()) }
                .map { style -> measurer.line(name, style, room) }
                .let { fits -> fits.firstOrNull { !it.hasVisualOverflow } ?: fits.last() }
            val at = Offset(size.width - 24f * u - mark.size.width, size.height - 22f * u - mark.size.height)
            drawText(mark, Color.Black.copy(alpha = 0.35f), at + Offset(0.8f * u, 1f * u))
            drawText(mark, Color.White, at)
        }
    }
}

private fun TextMeasurer.line(text: String, style: TextStyle, maxWidth: Float): TextLayoutResult =
    measure(text, style, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1, constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(1)))

/** Raised lettering: a shadow below and to the right, a rim of light above, then the ink. */
private fun DrawScope.embossed(text: TextLayoutResult, at: Offset, finish: CardFinish, u: Float, alpha: Float = 1f) {
    drawText(text, Color.Black.copy(alpha = 0.55f * alpha), at + Offset(0.9f * u, 1.1f * u))
    drawText(text, Color.White.copy(alpha = 0.22f * alpha), at - Offset(0.4f * u, 0.5f * u))
    drawText(text, Brush.verticalGradient(finish.ink), at, alpha = alpha)
}

/** The card itself: colour, light, an engraved guilloche pattern and a bevelled edge. */
private fun DrawScope.drawCardStock(finish: CardFinish, u: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.linearGradient(finish.base, start = Offset.Zero, end = Offset(w, h)))

    // Light from the upper left, and the finish's own colour pooling in the lower right.
    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), center = Offset(w * 0.08f, -h * 0.1f), radius = w * 0.85f))
    drawRect(Brush.radialGradient(listOf(finish.glow.copy(alpha = 0.26f), Color.Transparent), center = Offset(w * 0.98f, h * 1.1f), radius = w * 0.62f))

    // Guilloche: two families of fine rings whose crossings read as engraving, like a banknote.
    val hairline = Stroke(0.55f * u)
    val lower = Offset(w * 0.96f, h * 1.42f)
    val upper = Offset(-w * 0.12f, -h * 0.55f)
    var radius = 60f * u
    while (radius < 470f * u) {
        drawCircle(Color.White.copy(alpha = 0.055f), radius, lower, style = hairline)
        drawCircle(Color.White.copy(alpha = 0.03f), radius * 0.92f, upper, style = hairline)
        radius += 8.5f * u
    }

    // A band of reflected light crossing the card on the diagonal.
    drawRect(
        Brush.linearGradient(
            0.00f to Color.Transparent,
            0.36f to Color.Transparent,
            0.47f to Color.White.copy(alpha = 0.11f),
            0.53f to Color.White.copy(alpha = 0.03f),
            0.62f to Color.Transparent,
            1.00f to Color.Transparent,
            start = Offset(0f, h * 0.1f),
            end = Offset(w, h * 0.9f),
        ),
    )

    // Bevelled edge: bright where the light lands, dim on the far side.
    val inset = 0.7f * u
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f)),
            start = Offset.Zero,
            end = Offset(w, h),
        ),
        topLeft = Offset(inset, inset),
        size = Size(w - inset * 2, h - inset * 2),
        cornerRadius = CornerRadius(h * 0.07f - inset),
        style = Stroke(1.2f * u),
    )
}

private val ChipGold = listOf(Color(0xFFFFF1B8), Color(0xFFE9C261), Color(0xFFB98A2B), Color(0xFFF6DC8C), Color(0xFFA6781D))
private val ChipGroove = Color(0xFF6E4E0C)

/** A gold EMV contact chip, 46 by 35 units: brushed plate, engraved contact pads, bevelled rim. */
private fun DrawScope.drawChip(origin: Offset, u: Float) {
    val w = 46f * u
    val h = 35f * u
    val corner = CornerRadius(6.5f * u)
    val plate = Size(w, h)

    // Shadow the plate casts on the card, then the plate.
    drawRoundRect(Color.Black.copy(alpha = 0.38f), origin + Offset(0.8f * u, 1.6f * u), plate, corner)
    drawRoundRect(Brush.linearGradient(ChipGold, start = origin, end = origin + Offset(w, h)), origin, plate, corner)

    // Brushed metal: faint horizontal grain.
    clipRect(origin.x + 2f * u, origin.y + 2f * u, origin.x + w - 2f * u, origin.y + h - 2f * u) {
        var y = origin.y + 2.5f * u
        while (y < origin.y + h) {
            drawLine(Color.White.copy(alpha = 0.10f), Offset(origin.x, y), Offset(origin.x + w, y), 0.35f * u)
            y += 1.7f * u
        }
    }

    // Contact pads. Each groove is cut dark, with a thin lit lip just below it.
    fun groove(from: Offset, to: Offset) {
        drawLine(Color.White.copy(alpha = 0.55f), origin + from + Offset(0.5f * u, 0.6f * u), origin + to + Offset(0.5f * u, 0.6f * u), 0.6f * u, StrokeCap.Round)
        drawLine(ChipGroove.copy(alpha = 0.85f), origin + from, origin + to, 0.9f * u, StrokeCap.Round)
    }
    val left = w * 0.34f
    val right = w * 0.66f
    val top = h * 0.27f
    val bottom = h * 0.73f
    groove(Offset(0f, h * 0.36f), Offset(left, h * 0.36f))
    groove(Offset(0f, h * 0.64f), Offset(left, h * 0.64f))
    groove(Offset(right, h * 0.36f), Offset(w, h * 0.36f))
    groove(Offset(right, h * 0.64f), Offset(w, h * 0.64f))
    groove(Offset(w * 0.5f, 0f), Offset(w * 0.5f, top))
    groove(Offset(w * 0.5f, bottom), Offset(w * 0.5f, h))
    val pad = Size(right - left, bottom - top)
    val padCorner = CornerRadius(3.2f * u)
    drawRoundRect(Color.White.copy(alpha = 0.5f), origin + Offset(left + 0.5f * u, top + 0.6f * u), pad, padCorner, style = Stroke(0.6f * u))
    drawRoundRect(ChipGroove.copy(alpha = 0.85f), origin + Offset(left, top), pad, padCorner, style = Stroke(0.9f * u))

    // Rim: lit along the top left, shaded along the bottom right.
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent, Color(0xFF5C3F06).copy(alpha = 0.7f)), start = origin, end = origin + Offset(w, h)),
        origin,
        plate,
        corner,
        style = Stroke(0.9f * u),
    )
}

/** The four-wave contactless mark, centred vertically on [at] and opening to the right. */
private fun DrawScope.drawContactless(at: Offset, u: Float) {
    val stroke = Stroke(1.9f * u, cap = StrokeCap.Round)
    for (i in 0..3) {
        val radius = (4.5f + i * 4.4f) * u
        drawArc(
            color = Color.White.copy(alpha = 0.82f),
            startAngle = -36f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = Offset(at.x - radius - 4f * u, at.y - radius),
            size = Size(radius * 2, radius * 2),
            style = stroke,
        )
    }
}
