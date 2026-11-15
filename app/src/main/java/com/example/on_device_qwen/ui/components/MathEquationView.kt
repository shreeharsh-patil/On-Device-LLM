package com.example.on_device_qwen.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.ui.designsystem.SovereignElevation
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import com.example.on_device_qwen.ui.designsystem.SovereignTypography

/**
 * Renders mathematical block equations ($$ ... $$) using Sovereign editorial serif typography
 * and theme-aware Vellum/Obsidian surface contrast.
 */
@Composable
fun MathEquationView(
    equation: String,
    modifier: Modifier = Modifier
) {
    val colors = SovereignTheme.colors
    val scrollState = rememberScrollState()
    val formattedMath = remember(equation) { formatLatexSymbols(equation.trim()) }

    Surface(
        shape = SovereignTheme.shapes.sm,
        color = colors.surfaceLow,
        border = BorderStroke(SovereignElevation.hairlineWidth, colors.hairlineSubtle),
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
                fontFamily = SovereignTypography.EditorialSerifFontFamily,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                fontStyle = FontStyle.Italic,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val FRAC_REGEX = Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")
private val TEXT_CMD_REGEX = Regex("""\\(?:text|mathrm|mathbf|mathit)\{([^{}]*)\}""")

/**
 * Replaces common LaTeX math commands with crisp Unicode mathematical typography.
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
        .replace("\\rho", "ρ")
        .replace("\\sigma", "σ")
        .replace("\\phi", "φ")
        .replace("\\psi", "ψ")
        .replace("\\omega", "ω")
        .replace("\\Delta", "Δ")
        .replace("\\Theta", "Θ")
        .replace("\\Lambda", "Λ")
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
        .replace("\\equiv", "≡")
        .replace("\\leftrightarrow", "↔")
        .replace("\\Rightarrow", "⇒")
        .replace("\\leftarrow", "←")
        .replace("\\rightarrow", "→")
        .replace("\\to", "→")
        .replace("\\sqrt", "√")
        .replace("\\partial", "∂")
        .replace("\\nabla", "∇")
        .replace("\\cdot", "·")
        .replace("\\forall", "∀")
        .replace("\\exists", "∃")
        .replace("\\emptyset", "∅")
        .replace("\\notin", "∉")
        .replace("\\in", "∈")
        .replace("\\subset", "⊂")
        .replace("\\subseteq", "⊆")
        .replace("\\cup", "∪")
        .replace("\\cap", "∩")
        .replace("\\degree", "°")
        .replace("^2", "²")
        .replace("^3", "³")
        .replace("^n", "ⁿ")

    text = TEXT_CMD_REGEX.replace(text) { it.groupValues[1] }

    // Unwrap up to 2 levels of nested \frac{a}{b} cleanly
    repeat(2) {
        text = FRAC_REGEX.replace(text) { match ->
            "(${match.groupValues[1]} / ${match.groupValues[2]})"
        }
    }

    return text
}
