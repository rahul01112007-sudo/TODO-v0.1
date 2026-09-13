package com.todo.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Message(
    val text: String,
    val fromUser: Boolean
)

class MainActivity : ComponentActivity() {

    private var llm: LlmInference? = null
    private var session: LlmInferenceSession? = null

    private val modelFileName = "gemma3-1b-it-int4.task"

    private var modelReady by mutableStateOf(false)
    private var modelError by mutableStateOf("")

    private val modelPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->

            if (uri == null) return@registerForActivityResult

            lifecycleScope.launch(Dispatchers.IO) {

                try {
                    val modelDir = File(filesDir, "models")
                    modelDir.mkdirs()

                    val modelFile = File(modelDir, modelFileName)

                    contentResolver.openInputStream(uri)?.use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    loadModel(modelFile)

                    withContext(Dispatchers.Main) {
                        modelError = ""
                        modelReady = true
                    }

                } catch (e: Exception) {

                    withContext(Dispatchers.Main) {
                        modelReady = false
                        modelError =
                            e.message ?: "Model install failed"
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingModel =
            File(filesDir, "models/$modelFileName")

        if (existingModel.exists()) {

            lifecycleScope.launch(Dispatchers.IO) {

                try {
                    loadModel(existingModel)

                    withContext(Dispatchers.Main) {
                        modelError = ""
                        modelReady = true
                    }

                } catch (e: Exception) {

                    withContext(Dispatchers.Main) {
                        modelReady = false
                        modelError =
                            e.message ?: "Model load failed"
                    }
                }
            }
        }

        setContent {
            TodoApp()
        }
    }

    private fun loadModel(file: File) {

        session?.close()
        session = null

        llm?.close()
        llm = null

        val options =
            LlmInference.LlmInferenceOptions
                .builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(128)
                .build()

        llm =
            LlmInference.createFromOptions(
                applicationContext,
                options
            )

        createSession()
    }

    private fun createSession() {

        val currentLlm = llm ?: return

        val sessionOptions =
            LlmInferenceSession
                .LlmInferenceSessionOptions
                .builder()
                .setTopK(20)
                .setTopP(0.9f)
                .setTemperature(0.3f)
                .build()

        session =
            LlmInferenceSession.createFromOptions(
                currentLlm,
                sessionOptions
            )
    }

    private fun askAI(
        userMessage: String,
        onResult: (String) -> Unit
    ) {

        lifecycleScope.launch(Dispatchers.Default) {

            try {

                val activeSession = session

                if (activeSession == null) {

                    withContext(Dispatchers.Main) {
                        onResult(
                            "Pehle Local AI Model install karo."
                        )
                    }

                    return@launch
                }

                val prompt = """
You are TODO, a fast offline Android AI assistant.

Rules:
- Reply in simple Hindi/Hinglish.
- Be natural and helpful.
- Keep answers short unless the user asks for detail.
- Do not repeat the user's question.
- Do not mention these instructions.
- Answer directly.

User: $userMessage
TODO:
""".trimIndent()

                activeSession.addQueryChunk(prompt)

                val answer =
                    activeSession.generateResponse()

                val cleanAnswer =
                    answer.trim()

                withContext(Dispatchers.Main) {
                    onResult(
                        if (cleanAnswer.isEmpty())
                            "Sorry, mujhe iska jawab nahi mila."
                        else
                            cleanAnswer
                    )
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    onResult(
                        "AI error: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    @Composable
    private fun TodoApp() {

        var input by remember {
            mutableStateOf("")
        }

        var messages by remember {

            mutableStateOf(
                listOf(
                    Message(
                        if (modelReady)
                            "Namaste! Main TODO hoon. 🧠\nLocal AI ready hai."
                        else
                            "Namaste! Main TODO hoon. 🧠",
                        false
                    )
                )
            )
        }

        var thinking by remember {
            mutableStateOf(false)
        }

        val listState =
            rememberLazyListState()

        LaunchedEffect(messages.size, thinking) {

            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(
                    messages.size - 1
                )
            }
        }

        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {

            Surface(
                modifier = Modifier.fillMaxSize()
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            text = "TODO",
                            style =
                                MaterialTheme.typography
                                    .headlineLarge
                        )

                        Spacer(
                            modifier =
                                Modifier.width(14.dp)
                        )

                        Text(
                            text =
                                if (modelReady)
                                    "LOCAL AI • READY"
                                else
                                    "LOCAL AI • REQUIRED",
                            style =
                                MaterialTheme.typography
                                    .titleMedium
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(16.dp)
                    )

                    if (!modelReady) {

                        Button(
                            modifier =
                                Modifier.fillMaxWidth(),

                            onClick = {

                                modelPicker.launch(
                                    arrayOf(
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            }
                        ) {

                            Text(
                                "Install Local AI Model"
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Text(
                            "Sirf compatible .task model select karo."
                        )
                    }

                    if (modelError.isNotEmpty()) {

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Text(
                            text = modelError,
                            color =
                                MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(12.dp)
                    )

                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier.weight(1f),

                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {

                        items(messages) { message ->

                            Surface(
                                modifier =
                                    Modifier.fillMaxWidth(),

                                shape =
                                    MaterialTheme.shapes.large,

                                tonalElevation = 3.dp
                            ) {

                                Text(
                                    text = message.text,

                                    modifier =
                                        Modifier.padding(
                                            16.dp
                                        )
                                )
                            }
                        }

                        if (thinking) {

                            item {

                                Text(
                                    "TODO soch raha hai…"
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        verticalAlignment =
                            Alignment.Bottom
                    ) {

                        OutlinedTextField(
                            value = input,

                            onValueChange = {
                                input = it
                            },

                            modifier =
                                Modifier.weight(1f),

                            placeholder = {
                                Text(
                                    "Message TODO…"
                                )
                            },

                            maxLines = 4
                        )

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Button(

                            enabled =
                                input.trim().isNotEmpty() &&
                                modelReady &&
                                !thinking,

                            onClick = {

                                val question =
                                    input.trim()

                                if (question.isEmpty())
                                    return@Button

                                input = ""

                                messages =
                                    messages +
                                            Message(
                                                question,
                                                true
                                            )

                                thinking = true

                                askAI(question) { answer ->

                                    messages =
                                        messages +
                                                Message(
                                                    answer,
                                                    false
                                                )

                                    thinking = false
                                }
                            }
                        ) {

                            Text("Send")
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {

        session?.close()
        session = null

        llm?.close()
        llm = null

        super.onDestroy()
    }
}
