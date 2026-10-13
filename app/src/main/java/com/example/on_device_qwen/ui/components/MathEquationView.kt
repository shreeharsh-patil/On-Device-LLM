package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders mathematical block equations (like $$ ... $$) in a refined LaTeX-inspired presentation.
 */
@Composable
fun MathEquationView(
    equation: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val formattedMath = remember(equation) { formatLatexSymbols(equation.trim()) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formattedMath,
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Replaces common LaTeX math commands with unicode mathematical typography.
 */
fun formatLatexSymbols(raw: String): String {
    var text = raw
        .replace("\\alpha", "α")
        .replace("\\beta", "β")
        .replace("\\gamma", "γ")
        .replace("\\delta", "δ")
        .replace("\\epsilon", "ε")
        .replace("\\theta", "θ")
        .replace("\\lambda", "λ")
        .replace("\\mu", "μ")
        .replace("\\pi", "π")
        .replace("\\sigma", "σ")
        .replace("\\omega", "ω")
        .replace("\\Delta", "Δ")
        .replace("\\Sigma", "Σ")
        .replace("\\Omega", "Ω")
        .replace("\\infty", "∞")
        .replace("\\sum", "∑")
        .replace("\\prod", "∏")
        .replace("\\int", "∫")
        .replace("\\times", "×")
        .replace("\\div", "÷")
        .replace("\\pm", "±")
        .replace("\\neq", "≠")
        .replace("\\leq", "≤")
        .replace("\\geq", "≥")
        .replace("\\approx", "≈")
        .replace("\\to", "→")
        .replace("\\rightarrow", "→")
        .replace("\\sqrt", "√")
        .replace("\\partial", "∂")
        .replace("\\nabla", "∇")
        .replace("\\cdot", "·")
        .replace("\\in", "∈")
        .replace("\\subset", "⊂")
        .replace("\\cup", "∪")
        .replace("\\cap", "∩")

    // Handle fractions: \frac{a}{b} -> (a / b)
    val fracRegex = Regex("""\\frac\{([^}]+)\}\{([^}]+)\}""")
    text = fracRegex.replace(text) { match ->
        "(${match.groupValues[1]} / ${match.groupValues[2]})"
    }

    return text
}
