package com.todo.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

data class Message(val text: String, val fromTodo: Boolean)

class TodoViewModel : ViewModel() {
    var messages by mutableStateOf(
        listOf(
            Message("Hello! Main TODO hoon. Abhi mera offline chat core active hai. 🧠", true),
            Message("Tum mujhe type karke command de sakte ho.", true)
        )
    )
        private set

    fun send(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        messages = messages + Message(clean, false)

        val reply = when {
            clean.equals("hello", true) || clean.equals("hi", true) ->
                "Hello! Main yahin hoon. Batao kya karna hai?"
            clean.contains("who are you", true) ->
                "Main TODO hoon — tumhara private personal AI assistant."
            clean.contains("offline", true) ->
                "TODO ka design local-first hai. Is version mein koi cloud AI API use nahi ho rahi."
            else ->
                "Maine tumhari command receive kar li. Local LLM aur phone tools next versions mein connect kiye jayenge."
        }
        messages = messages + Message(reply, true)
    }
}

@Composable
fun TodoApp(vm: TodoViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7C9CFF),
            background = Color(0xFF101114),
            surface = Color(0xFF17181D)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("TODO", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text("OFFLINE • v0.1", style = MaterialTheme.typography.labelMedium)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vm.messages) { msg ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.fromTodo) Arrangement.Start else Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (msg.fromTodo) Color(0xFF1D2028) else Color(0xFF30466F),
                                modifier = Modifier.widthIn(max = 330.dp)
                            ) {
                                Text(msg.text, Modifier.padding(14.dp))
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message TODO…") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TodoApp() }
    }
}
