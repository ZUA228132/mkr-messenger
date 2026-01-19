package com.pioneer.messenger.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Фейковый экран калькулятора
 * 
 * Работает как обычный калькулятор, но при вводе секретного кода
 * открывает настоящий мессенджер
 */
@Composable
fun FakeCalculatorScreen(
    secretCode: String,
    onSecretCodeEntered: () -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var currentInput by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf<String?>(null) }
    var firstOperand by remember { mutableStateOf<Double?>(null) }
    
    // Проверка секретного кода
    LaunchedEffect(currentInput) {
        if (currentInput == secretCode) {
            onSecretCodeEntered()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(16.dp)
    ) {
        // Дисплей
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                maxLines = 1
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Кнопки
        val buttons = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )
        
        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { button ->
                    val isZero = button == "0"
                    val isOperator = button in listOf("÷", "×", "−", "+", "=")
                    val isFunction = button in listOf("C", "±", "%")
                    
                    CalculatorButton(
                        text = button,
                        modifier = Modifier
                            .weight(if (isZero) 2f else 1f)
                            .aspectRatio(if (isZero) 2.2f else 1f),
                        backgroundColor = when {
                            isOperator -> Color(0xFFFF9500)
                            isFunction -> Color(0xFFA5A5A5)
                            else -> Color(0xFF333333)
                        },
                        textColor = if (isFunction) Color.Black else Color.White,
                        onClick = {
                            handleButtonClick(
                                button = button,
                                display = display,
                                currentInput = currentInput,
                                operator = operator,
                                firstOperand = firstOperand,
                                onDisplayChange = { display = it },
                                onCurrentInputChange = { currentInput = it },
                                onOperatorChange = { operator = it },
                                onFirstOperandChange = { firstOperand = it }
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}


@Composable
private fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = backgroundColor,
        shape = CircleShape
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}

private fun handleButtonClick(
    button: String,
    display: String,
    currentInput: String,
    operator: String?,
    firstOperand: Double?,
    onDisplayChange: (String) -> Unit,
    onCurrentInputChange: (String) -> Unit,
    onOperatorChange: (String?) -> Unit,
    onFirstOperandChange: (Double?) -> Unit
) {
    when (button) {
        "C" -> {
            onDisplayChange("0")
            onCurrentInputChange("")
            onOperatorChange(null)
            onFirstOperandChange(null)
        }
        "±" -> {
            if (display != "0") {
                val newValue = if (display.startsWith("-")) {
                    display.substring(1)
                } else {
                    "-$display"
                }
                onDisplayChange(newValue)
            }
        }
        "%" -> {
            val value = display.toDoubleOrNull() ?: 0.0
            onDisplayChange(formatResult(value / 100))
        }
        "." -> {
            if (!display.contains(".")) {
                onDisplayChange("$display.")
            }
        }
        "=" -> {
            if (operator != null && firstOperand != null) {
                val secondOperand = display.toDoubleOrNull() ?: 0.0
                val result = calculate(firstOperand, secondOperand, operator)
                onDisplayChange(formatResult(result))
                onOperatorChange(null)
                onFirstOperandChange(null)
                onCurrentInputChange("")
            }
        }
        in listOf("÷", "×", "−", "+") -> {
            onFirstOperandChange(display.toDoubleOrNull())
            onOperatorChange(button)
            onCurrentInputChange("")
            onDisplayChange("0")
        }
        else -> {
            // Цифры - добавляем к секретному коду
            val newInput = currentInput + button
            onCurrentInputChange(newInput)
            
            // Обновляем дисплей
            val newDisplay = if (display == "0") button else display + button
            onDisplayChange(newDisplay)
        }
    }
}

private fun calculate(first: Double, second: Double, operator: String): Double {
    return when (operator) {
        "+" -> first + second
        "−" -> first - second
        "×" -> first * second
        "÷" -> if (second != 0.0) first / second else 0.0
        else -> second
    }
}

private fun formatResult(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.8f", value).trimEnd('0').trimEnd('.')
    }
}
