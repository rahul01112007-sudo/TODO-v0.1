package com.todo.assistant

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Message(
    val text: String,
    val fromTodo: Boolean
)

class TodoViewModel : ViewModel() {

    var messages by mutableStateOf(
        listOf(
            Message(
                "Hello! Main TODO hoon. 🧠",
                true
            ),
            Message(
                "Pehle ek Local AI model install karo. Uske baad main bina Internet/API ke jawab generate karunga.",
                true
            )
        )
    )
        private set

    var modelInstalled by mutableStateOf(false)
        private set

    var generating by mutableStateOf(false)
        private set

    private var llm: LlmInference? = null

    fun installModel(context: Context, uri: Uri) {

        viewModelScope.launch {

            try {

                val modelFile = File(
                    context.filesDir,
                    "todo_model.task"
                )

                withContext(Dispatchers.IO) {

                    context.contentResolver
                        .openInputStream(uri)
                        ?.use { input ->

                            modelFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        ?: throw Exception("Model file read nahi ho saki.")
                }

                initializeModel(context, modelFile)

            } catch (e: Exception) {

                messages = messages + Message(
                    "Model install nahi ho saka.\n\nError: ${e.message}",
                    true
                )
            }
        }
    }

    private suspend fun initializeModel(
        context: Context,
        modelFile: File
    ) {

        withContext(Dispatchers.IO) {

            val options =
                LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(512)
                    .build()

            llm = LlmInference.createFromOptions(
                context,
                options
            )
        }

        modelInstalled = true

        messages = messages + Message(
            "✅ Local AI model ready hai!\n\nAb tum normal language mein mujhse baat kar sakte ho. Internet ki zarurat nahi.",
            true
        )
    }

    fun send(text: String) {

        val clean = text.trim()

        if (clean.isEmpty() || generating) return

        messages = messages + Message(
            clean,
            false
        )

        if (llm == null) {

            messages = messages + Message(
                "Pehle \"Install Local AI Model\" se ek compatible .task model install karo.",
                true
            )

            return
        }

        generating = true

        viewModelScope.launch {

            try {

                val prompt = buildPrompt(clean)

                val answer = withContext(Dispatchers.Default) {

                    llm!!.generateResponse(prompt)
                }

                messages = messages + Message(
                    answer,
                    true
                )

            } catch (e: Exception) {

                messages = messages + Message(
                    "AI response generate nahi ho saka.\n\nError: ${e.message}",
                    true
                )

            } finally {

                generating = false
            }
        }
    }

    private fun buildPrompt(userMessage: String): String {

        val history = messages
            .takeLast(10)
            .joinToString("\n") {

                if (it.fromTodo) {
                    "TODO: ${it.text}"
                } else {
                    "User: ${it.text}"
                }
            }

        return """
You are TODO, a helpful private offline Android assistant.

Rules:
- Answer naturally.
- Be concise but useful.
- You are running completely locally on the phone.
- Do not claim to have Internet access.
- Do not invent phone capabilities.
- Understand Hindi, Hinglish and English when possible.

Conversation:
$history

User:
$userMessage

TODO:
""".trimIndent()
    }

    fun checkExistingModel(context: Context) {

        viewModelScope.launch {

            val modelFile = File(
                context.filesDir,
                "todo_model.task"
            )

            if (modelFile.exists()) {

                try {
                    initializeModel(
                        context,
                        modelFile
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onCleared() {

        llm = null

        super.onCleared()
    }
}

@Composable
fun TodoApp(
    vm: TodoViewModel = viewModel()
) {

    val context = androidx.compose.ui.platform.LocalContext.current

    var input by remember {
        mutableStateOf("")
    }

    val modelPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri != null) {
                vm.installModel(
                    context,
                    uri
                )
            }
        }

    LaunchedEffect(Unit) {
        vm.checkExistingModel(context)
    }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {

        Surface(
            modifier = Modifier.fillMaxSize()
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        text = "TODO",
                        style =
                            MaterialTheme.typography.headlineMedium
                    )

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    Text(
                        text =
                            if (vm.modelInstalled)
                                "LOCAL AI • OFFLINE"
                            else
                                "OFFLINE • MODEL REQUIRED"
                    )
                }

                if (!vm.modelInstalled) {

                    Button(
                        onClick = {

                            modelPicker.launch(
                                arrayOf(
                                    "application/octet-stream",
                                    "application/*",
                                    "*/*"
                                )
                            )
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp
                            )
                    ) {

                        Text(
                            "Install Local AI Model"
                        )
                    }

                    Text(
                        text =
                            "Compatible .task LLM model select karo.",
                        modifier = Modifier.padding(
                            horizontal = 18.dp,
                            vertical = 6.dp
                        )
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),

                    contentPadding =
                        PaddingValues(14.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    items(vm.messages) { msg ->

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                if (msg.fromTodo)
                                    Arrangement.Start
                                else
                                    Arrangement.End
                        ) {

                            Surface(
                                modifier =
                                    Modifier.widthIn(
                                        max = 340.dp
                                    ),

                                color =
                                    if (msg.fromTodo)
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .primaryContainer
                            ) {

                                Text(
                                    text = msg.text,
                                    modifier =
                                        Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }

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
                            Text("Message TODO...")
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

                        enabled =
                            input.isNotBlank() &&
                            !vm.generating
                    ) {

                        Text(
                            if (vm.generating)
                                "..."
                            else
                                "Send"
                        )
                    }
                }
            }
        }
    }
}

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
