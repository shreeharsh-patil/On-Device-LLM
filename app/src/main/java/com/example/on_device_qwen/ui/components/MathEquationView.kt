package com.example.on_device_qwen.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.on_device_qwen.ui.designsystem.SovereignDivider
import com.example.on_device_qwen.ui.designsystem.SovereignHairlineCard
import com.example.on_device_qwen.ui.designsystem.SovereignIconButton
import com.example.on_device_qwen.ui.designsystem.SovereignTheme
import kotlinx.coroutines.delay

/**
 * Renders mathematical block equations ($$ ... $$) inside a Sovereign hairline frame
 * with an "Equation" micro-header, accessible Copy LaTeX button, and horizontal scroll.
 */
@Composable
fun MathEquationView(
    equation: String,
    modifier: Modifier = Modifier
) {
    val colors = SovereignTheme.colors
    val shapes = SovereignTheme.shapes
    val motion = SovereignTheme.motion
    val context = LocalContext.current

    val scrollState = rememberScrollState()
    val rawEquation = remember(equation) { equation.trim() }
    val formattedMath = remember(rawEquation) { formatLatexSymbols(rawEquation) }
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1800)
            isCopied = false
        }
    }

    SovereignHairlineCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = shapes.md,
        containerColor = colors.surfaceLow,
        borderColor = colors.hairlineSubtle,
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.codeHeader)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Equation",
                style = SovereignTheme.typography.monoTag,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = isCopied,
                    enter = fadeIn(motion.effectsQuick()),
                    exit = fadeOut(motion.effectsQuick())
                ) {
                    Text(
                        text = "Copied",
                        style = SovereignTheme.typography.monoTag,
                        color = colors.success,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }

                SovereignIconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("equation", rawEquation))
                        isCopied = true
                    },
                    contentDescription = if (isCopied) "Copied LaTeX" else "Copy LaTeX",
                    visualSize = 36.dp,
                    contentColor = if (isCopied) colors.success else colors.textSecondary,
                    hapticFeedback = true
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        SovereignDivider()

        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formattedMath,
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.5.sp,
                    lineHeight = 25.sp,
                    fontStyle = FontStyle.Italic,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Hoisted top-level regexes and symbol tables — zero per-call Regex allocations.
private val LATEX_NEWLINE_REGEX = Regex("""\\\\(?=\s|$)""")
private val LATEX_THIN_SPACE_REGEX = Regex("""\\[,;:!]""")
private val LATEX_FRAC_REGEX = Regex("""\\(?:d|t)?frac\{([^{}]+)\}\{([^{}]+)\}""")
private val LATEX_TEXT_WRAPPER_REGEX = Regex("""\\(?:text|mathrm|mathbf|mathit|mathsf|mathtt|mathcal|operatorname)\{([^{}]*)\}""")
private val LATEX_SQRT_BRACE_REGEX = Regex("""\\sqrt\{([^{}]+)\}""")
private val LATEX_COMMAND_REGEX = Regex("""\\([A-Za-z]+)""")
private val LATEX_SUPERSCRIPT_REGEX = Regex("""\^(?:\{([^}]+)\}|([0-9+\-=()a-zA-Z]))""")
private val LATEX_SUBSCRIPT_REGEX = Regex("""_(?:\{([^}]+)\}|([0-9+\-=()a-zA-Z]))""")

private val LATEX_SYMBOLS: Map<String, String> = mapOf(
    // Lowercase Greek
    "alpha" to "α",
    "beta" to "β",
    "gamma" to "γ",
    "delta" to "δ",
    "epsilon" to "ε",
    "varepsilon" to "ε",
    "zeta" to "ζ",
    "eta" to "η",
    "theta" to "θ",
    "vartheta" to "ϑ",
    "iota" to "ι",
    "kappa" to "κ",
    "lambda" to "λ",
    "mu" to "μ",
    "nu" to "ν",
    "xi" to "ξ",
    "pi" to "π",
    "rho" to "ρ",
    "sigma" to "σ",
    "tau" to "τ",
    "upsilon" to "υ",
    "phi" to "φ",
    "varphi" to "φ",
    "chi" to "χ",
    "psi" to "ψ",
    "omega" to "ω",
    // Uppercase Greek
    "Gamma" to "Γ",
    "Delta" to "Δ",
    "Theta" to "Θ",
    "Lambda" to "Λ",
    "Xi" to "Ξ",
    "Pi" to "Π",
    "Sigma" to "Σ",
    "Phi" to "Φ",
    "Psi" to "Ψ",
    "Omega" to "Ω",
    // Calculus, Algebra & Relations
    "infty" to "∞",
    "sum" to "∑",
    "prod" to "∏",
    "int" to "∫",
    "iint" to "∬",
    "oint" to "∮",
    "times" to "×",
    "div" to "÷",
    "pm" to "±",
    "mp" to "∓",
    "neq" to "≠",
    "ne" to "≠",
    "leq" to "≤",
    "le" to "≤",
    "geq" to "≥",
    "ge" to "≥",
    "approx" to "≈",
    "equiv" to "≡",
    "sim" to "∼",
    "propto" to "∝",
    "to" to "→",
    "rightarrow" to "→",
    "leftarrow" to "←",
    "leftrightarrow" to "↔",
    "Rightarrow" to "⇒",
    "Leftarrow" to "⇐",
    "Leftrightarrow" to "⇔",
    "mapsto" to "↦",
    "sqrt" to "√",
    "partial" to "∂",
    "nabla" to "∇",
    "cdot" to "·",
    "bullet" to "•",
    "circ" to "∘",
    "forall" to "∀",
    "exists" to "∃",
    "in" to "∈",
    "notin" to "∉",
    "subset" to "⊂",
    "subseteq" to "⊆",
    "supset" to "⊃",
    "supseteq" to "⊇",
    "cup" to "∪",
    "cap" to "∩",
    "emptyset" to "∅",
    "varnothing" to "∅",
    "therefore" to "∴",
    "because" to "∵",
    "angle" to "∠",
    "degree" to "°",
    "prime" to "′",
    "dots" to "…",
    "ldots" to "…",
    "cdots" to "⋯",
    // Delimiter & spacing commands
    "left" to "",
    "right" to "",
    "quad" to "  ",
    "qquad" to "    "
)

private val SUPERSCRIPT_MAP: Map<Char, Char> = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ'
)

private val SUBSCRIPT_MAP: Map<Char, Char> = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
    'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
    'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
    'v' to 'ᵥ', 'x' to 'ₓ'
)

/**
 * Replaces common LaTeX math commands with Unicode mathematical typography.
 * Uses precompiled top-level regexes for allocation-lean streaming performance.
 */
fun formatLatexSymbols(raw: String): String {
    if (raw.isEmpty()) return raw
    if (!raw.contains('\\') && !raw.contains('^') && !raw.contains('_')) return raw

    var text = raw
    if (text.contains("\\\\")) {
        text = LATEX_NEWLINE_REGEX.replace(text, "\n")
    }
    if (text.contains('\\')) {
        text = LATEX_THIN_SPACE_REGEX.replace(text, " ")
        var pass = 0
        while (pass < 3 && text.contains("frac{")) {
            val replaced = LATEX_FRAC_REGEX.replace(text) { match ->
                "(${match.groupValues[1]} / ${match.groupValues[2]})"
            }
            if (replaced == text) break
            text = replaced
            pass++
        }
        if (text.contains("\\sqrt{")) {
            text = LATEX_SQRT_BRACE_REGEX.replace(text) { match ->
                "√(${match.groupValues[1]})"
            }
        }
        text = LATEX_TEXT_WRAPPER_REGEX.replace(text) { match ->
            match.groupValues[1]
        }
        text = LATEX_COMMAND_REGEX.replace(text) { match ->
            LATEX_SYMBOLS[match.groupValues[1]] ?: match.value
        }
    }

    if (text.contains('^')) {
        text = LATEX_SUPERSCRIPT_REGEX.replace(text) { match ->
            val group = match.groupValues[1].ifEmpty { match.groupValues[2] }
            mapScriptChars(group, SUPERSCRIPT_MAP) ?: match.value
        }
    }

    if (text.contains('_')) {
        text = LATEX_SUBSCRIPT_REGEX.replace(text) { match ->
            val group = match.groupValues[1].ifEmpty { match.groupValues[2] }
            mapScriptChars(group, SUBSCRIPT_MAP) ?: match.value
        }
    }

    return text
}

private fun mapScriptChars(input: String, table: Map<Char, Char>): String? {
    if (input.isEmpty()) return null
    val sb = StringBuilder(input.length)
    for (ch in input) {
        val mapped = table[ch] ?: return null
        sb.append(mapped)
    }
    return sb.toString()
}
