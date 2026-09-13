package com.todo.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

data class Message(
    val text: String,
    val fromTodo: Boolean
)

class TodoViewModel : ViewModel() {

    var messages by mutableStateOf(
        listOf(
            Message(
                "Hello! Main TODO hoon. 🧠\nMain abhi 100% offline mode mein hoon.",
                true
            ),
            Message(
                "Tum mujhse normal commands try kar sakte ho, jaise: \"time kya hai\", \"date batao\", \"2 + 5\" ya \"tum kaun ho\".",
                true
            )
        )
    )
        private set

    fun send(text: String) {

        val clean = text.trim()

        if (clean.isEmpty()) return

        messages = messages + Message(clean, false)

        val reply = processCommand(clean)

        messages = messages + Message(reply, true)
    }

    private fun processCommand(input: String): String {

        val text = input
            .trim()
            .lowercase(Locale.getDefault())

        // -----------------------------
        // GREETINGS
        // -----------------------------

        if (
            text == "hi" ||
            text == "hello" ||
            text == "hey" ||
            text == "hii" ||
            text == "namaste" ||
            text == "नमस्ते"
        ) {
            return listOf(
                "Hello! 👋 Kaise ho?",
                "Hi! 👋 TODO yahin hai.",
                "Hello! Batao kya karna hai?",
                "Namaste! 🙏 Main ready hoon."
            ).random()
        }

        // -----------------------------
        // IDENTITY
        // -----------------------------

        if (
            text.contains("tum kaun") ||
            text.contains("who are you") ||
            text.contains("your name") ||
            text.contains("naam kya")
        ) {
            return "Main TODO hoon — tumhara private offline assistant. 🤖\nMere current version mein kisi cloud AI API ka use nahi ho raha."
        }

        // -----------------------------
        // OFFLINE
        // -----------------------------

        if (
            text.contains("offline") ||
            text.contains("internet ke bina") ||
            text.contains("bina internet")
        ) {
            return "Haan. Main offline-first design par bana hoon. 📱\nCurrent version mein response generate karne ke liye Internet ya AI API ki zarurat nahi hai."
        }

        // -----------------------------
        // TIME
        // -----------------------------

        if (
            text.contains("time") ||
            text.contains("samay") ||
            text.contains("kitne baje")
        ) {
            val time = SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            ).format(Date())

            return "Abhi time hai $time ⏰"
        }

        // -----------------------------
        // DATE
        // -----------------------------

        if (
            text.contains("date") ||
            text.contains("tarikh") ||
            text.contains("aaj ki date") ||
            text.contains("today")
        ) {
            val date = SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.getDefault()
            ).format(Date())

            return "Aaj ki date hai $date 📅"
        }

        // -----------------------------
        // DAY
        // -----------------------------

        if (
            text.contains("kaunsa din") ||
            text.contains("which day") ||
            text.contains("day today")
        ) {
            val day = SimpleDateFormat(
                "EEEE",
                Locale.getDefault()
            ).format(Date())

            return "Aaj $day hai. 📅"
        }

        // -----------------------------
        // CALCULATOR
        // -----------------------------

        val calculation = calculate(text)

        if (calculation != null) {
            return "Answer: $calculation 🧮"
        }

        // -----------------------------
        // HELP
        // -----------------------------

        if (
            text == "help" ||
            text.contains("kya kar sakte ho") ||
            text.contains("kya kya kar")
        ) {
            return """
Abhi main ye offline commands samajh sakta hoon:

• Hi / Hello
• Tum kaun ho?
• Time kya hai?
• Aaj ki date batao
• Aaj kaunsa din hai?
• Simple calculation: 25 + 30
• Offline mode
• Help

Aage hum ismein Notes, Tasks, Reminder, Calculator aur phone ke local tools add karenge.
""".trimIndent()
        }

        // -----------------------------
        // THANK YOU
        // -----------------------------

        if (
            text.contains("thank") ||
            text.contains("thanks") ||
            text.contains("shukriya")
        ) {
            return listOf(
                "You're welcome! 😊",
                "Koi baat nahi! 👍",
                "Hamesha! 😄"
            ).random()
        }

        // -----------------------------
        // SIMPLE EMOTION / CHAT
        // -----------------------------

        if (
            text.contains("kaise ho") ||
            text.contains("how are you")
        ) {
            return "Main bilkul ready hoon! 😄 Tum batao?"
        }

        if (
            text.contains("good morning")
        ) {
            return "Good morning! ☀️ Aaj kya karna hai?"
        }

        if (
            text.contains("good night")
        ) {
            return "Good night! 🌙"
        }

        // -----------------------------
        // UNKNOWN COMMAND
        // -----------------------------

        return """
Maine tumhari command samajhne ki koshish ki:

"$input"

Is command ka offline skill abhi mere andar add nahi hai.

Hum TODO ko step-by-step aur powerful banayenge — bina kisi AI API ke.
""".trimIndent()
    }

    // ==========================================
    // LOCAL CALCULATOR
    // ==========================================

    private fun calculate(input: String): String? {

        var expression = input

        expression = expression
            .replace("calculate", "")
            .replace("calc", "")
            .replace("what is", "")
            .replace("kitna", "")
            .replace("=?", "")
            .replace("=", "")
            .trim()

        // Hindi/common operators
        expression = expression
            .replace("plus", "+")
            .replace("add", "+")
            .replace("minus", "-")
            .replace("subtract", "-")
            .replace("multiply", "*")
            .replace("×", "*")
            .replace("into", "*")
            .replace("divide", "/")
            .replace("÷", "/")

        // Only allow calculator characters
        if (!expression.matches(
                Regex("""[0-9+\-*/().%\s]+""")
            )
        ) {
            return null
        }

        if (
            !expression.any { it.isDigit() } ||
            !expression.any { "+-*/%".contains(it) }
        ) {
            return null
        }

        return try {
            val result = SimpleExpressionParser(expression).parse()

            if (result.isNaN() || result.isInfinite()) {
                null
            } else {
                if (result % 1.0 == 0.0) {
                    result.toLong().toString()
                } else {
                    "%.4f".format(Locale.US, result)
                }
            }

        } catch (_: Exception) {
            null
        }
    }
}

// ==========================================
// SMALL LOCAL MATH PARSER
// ==========================================

class SimpleExpressionParser(
    private val expression: String
) {

    private var position = 0

    fun parse(): Double {

        val result = parseExpression()

        skipSpaces()

        if (position != expression.length) {
            throw IllegalArgumentException("Invalid expression")
        }

        return result
    }

    private fun parseExpression(): Double {

        var result = parseTerm()

        while (true) {

            skipSpaces()

            if (match('+')) {
                result += parseTerm()

            } else if (match('-')) {
                result -= parseTerm()

            } else {
                return result
            }
        }
    }

    private fun parseTerm(): Double {

        var result = parseFactor()

        while (true) {

            skipSpaces()

            if (match('*')) {
                result *= parseFactor()

            } else if (match('/')) {

                val divisor = parseFactor()

                if (divisor == 0.0) {
                    throw ArithmeticException("Division by zero")
                }

                result /= divisor

            } else if (match('%')) {

                val divisor = parseFactor()

                if (divisor == 0.0) {
                    throw ArithmeticException("Division by zero")
                }

                result %= divisor

            } else {
                return result
            }
        }
    }

    private fun parseFactor(): Double {

        skipSpaces()

        if (match('+')) {
            return parseFactor()
        }

        if (match('-')) {
            return -parseFactor()
        }

        if (match('(')) {

            val result = parseExpression()

            if (!match(')')) {
                throw IllegalArgumentException("Missing )")
            }

            return result
        }

        return parseNumber()
    }

    private fun parseNumber(): Double {

        skipSpaces()

        val start = position

        while (
            position < expression.length &&
            (
                expression[position].isDigit() ||
                expression[position] == '.'
            )
        ) {
            position++
        }

        if (start == position) {
            throw IllegalArgumentException("Number expected")
        }

        return expression.substring(
            start,
            position
        ).toDouble()
    }

    private fun match(character: Char): Boolean {

        skipSpaces()

        if (
            position < expression.length &&
            expression[position] == character
        ) {
            position++
            return true
        }

        return false
    }

    private fun skipSpaces() {

        while (
            position < expression.length &&
            expression[position].isWhitespace()
        ) {
            position++
        }
    }
}

// ==========================================
// UI
// ==========================================

@Composable
fun TodoApp(
    vm: TodoViewModel = viewModel()
) {

    var input by remember {
        mutableStateOf("")
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C9CFF),
            background = Color(0xFF101114),
            surface = Color(0xFF17181D)
        )
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                // HEADER

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "TODO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    Text(
                        text = "OFFLINE • v0.1",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // CHAT

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),

                    contentPadding = PaddingValues(
                        horizontal = 14.dp,
                        vertical = 8.dp
                    ),

                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    items(vm.messages) { msg ->

                        Row(
                            modifier = Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                if (msg.fromTodo)
                                    Arrangement.Start
                                else
                                    Arrangement.End
                        ) {

                            Surface(
                                shape = RoundedCornerShape(18.dp),

                                color =
                                    if (msg.fromTodo)
                                        Color(0xFF1D2028)
                                    else
                                        Color(0xFF30466F),

                                modifier = Modifier.widthIn(
                                    max = 330.dp
                                )
                            ) {

                                Text(
                                    text = msg.text,

                                    modifier = Modifier.padding(
                                        14.dp
                                    )
                                )
                            }
                        }
                    }
                }

                // INPUT

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    OutlinedTextField(
                        value = input,

                        onValueChange = {
                            input = it
                        },

                        modifier = Modifier.weight(1f),

                        placeholder = {
                            Text("Message TODO…")
                        },

                        singleLine = true
                    )

                    Spacer(
                        modifier = Modifier.width(8.dp)
                    )

                    Button(
                        onClick = {

                            vm.send(input)

                            input = ""

                        },

                        enabled = input.isNotBlank()
                    ) {

                        Text("Send")
                    }
                }
            }
        }
    }
}

// ==========================================
// MAIN ACTIVITY
// ==========================================

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContent {
            TodoApp()
        }
    }
}
