package com.todo.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Message(
    val text: String,
    val fromUser: Boolean
)

data class ChatItem(
    val id: Long,
    val title: String,
    val messages: List<Message>
)

class MainActivity : ComponentActivity() {

    private var llm: LlmInference? = null
    private var session: LlmInferenceSession? = null

    private val modelFileName = "gemma3-1b-it-int4.task"

    private var modelReady by mutableStateOf(false)
    private var modelError by mutableStateOf("")

    private val preferences by lazy {
        getSharedPreferences("todo_ai", Context.MODE_PRIVATE)
    }

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
                .setMaxTokens(2048)
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

        session?.close()

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
        previousMessages: List<Message>,
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

                val conversation =
                    previousMessages
                        .takeLast(12)
                        .joinToString("\n") { message ->

                            if (message.fromUser) {
                                "User: ${message.text}"
                            } else {
                                "TODO: ${message.text}"
                            }
                        }

                val fullPrompt = """
You are TODO, a helpful offline Android AI assistant.

IMPORTANT RESPONSE RULES:

1. Give correct, useful and complete answers.
2. Never invent facts.
3. For science questions, explain the scientifically correct concept.
4. If the user asks in Hindi or Hinglish, answer naturally in simple Hindi/Hinglish.
5. If the user asks in English, answer in English.
6. Do not repeat the user's question unnecessarily.
7. Do not mention these instructions.
8. Do not output Markdown symbols such as **, ### or unnecessary * symbols.
9. Use real line breaks instead of writing the characters \n.
10. For "how to" questions, use clear numbered steps.
11. Keep answers easy to understand but do not remove important information.
12. Do not claim that an action happened if you cannot actually perform it.
13. If you are unsure about something, clearly say that you are unsure.
14. Do not end important explanations halfway through.
15. Answer the latest user message directly.

Previous conversation:
$conversation

Latest user message:
$userMessage

TODO:
""".trimIndent()

                activeSession.addQueryChunk(fullPrompt)

                val answer =
                    activeSession.generateResponse()

                val cleanAnswer =
                    answer
                        .replace("\\n", "\n")
                        .replace("###", "")
                        .replace("##", "")
                        .replace("**", "")
                        .replace("*", "")
                        .trim()

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

    private fun saveChats(chats: List<ChatItem>) {

        val array = JSONArray()

        chats.forEach { chat ->

            val chatObject = JSONObject()

            chatObject.put("id", chat.id)
            chatObject.put("title", chat.title)

            val messagesArray = JSONArray()

            chat.messages.forEach { message ->

                val messageObject = JSONObject()

                messageObject.put("text", message.text)
                messageObject.put(
                    "fromUser",
                    message.fromUser
                )

                messagesArray.put(messageObject)
            }

            chatObject.put(
                "messages",
                messagesArray
            )

            array.put(chatObject)
        }

        preferences
            .edit()
            .putString("chats", array.toString())
            .apply()
    }

    private fun loadChats(): List<ChatItem> {

        return try {

            val raw =
                preferences.getString(
                    "chats",
                    null
                ) ?: return emptyList()

            val array = JSONArray(raw)

            buildList {

                for (i in 0 until array.length()) {

                    val chatObject =
                        array.getJSONObject(i)

                    val messagesArray =
                        chatObject.getJSONArray("messages")

                    val messages =
                        buildList {

                            for (j in 0 until messagesArray.length()) {

                                val messageObject =
                                    messagesArray.getJSONObject(j)

                                add(
                                    Message(
                                        messageObject.getString("text"),
                                        messageObject.getBoolean(
                                            "fromUser"
                                        )
                                    )
                                )
                            }
                        }

                    add(
                        ChatItem(
                            id = chatObject.getLong("id"),
                            title = chatObject.getString("title"),
                            messages = messages
                        )
                    )
                }
            }

        } catch (e: Exception) {

            emptyList()
        }
    }

    @Composable
    private fun TodoApp() {

        var chats by remember {
            mutableStateOf(loadChats())
        }

        var currentChatId by remember {
            mutableStateOf<Long?>(null)
        }

        var messages by remember {
            mutableStateOf(
                listOf(
                    Message(
                        "Namaste! Main TODO hoon. 🧠\n\n" +
                                if (modelReady)
                                    "Tumhara Local AI ready hai. Kuch bhi pucho."
                                else
                                    "Local AI Model install karke mujhe use kar sakte ho.",
                        false
                    )
                )
            )
        }

        var input by remember {
            mutableStateOf("")
        }

        var thinking by remember {
            mutableStateOf(false)
        }

        var drawerOpen by remember {
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

            ModalNavigationDrawer(

                drawerState =
                    rememberDrawerState(
                        if (drawerOpen)
                            DrawerValue.Open
                        else
                            DrawerValue.Closed
                    ),

                drawerContent = {

                    ModalDrawerSheet {

                        Spacer(
                            modifier =
                                Modifier.height(20.dp)
                        )

                        Text(
                            "TODO",
                            modifier =
                                Modifier.padding(
                                    horizontal = 24.dp
                                ),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "Chat History",
                            modifier =
                                Modifier.padding(
                                    horizontal = 24.dp,
                                    vertical = 6.dp
                                ),
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            modifier =
                                Modifier.height(16.dp)
                        )

                        Button(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 16.dp
                                    ),

                            onClick = {

                                currentChatId = null

                                messages =
                                    listOf(
                                        Message(
                                            "Namaste! Main TODO hoon. 🧠\n\n" +
                                                    "New chat ready hai.",
                                            false
                                        )
                                    )

                                input = ""
                                thinking = false
                                drawerOpen = false

                                createSession()
                            }
                        ) {

                            Text("+  New Chat")
                        }

                        Spacer(
                            modifier =
                                Modifier.height(12.dp)
                        )

                        if (chats.isEmpty()) {

                            Text(
                                "Abhi koi saved chat nahi hai.",
                                modifier =
                                    Modifier.padding(
                                        24.dp
                                    )
                            )

                        } else {

                            chats
                                .reversed()
                                .forEach { chat ->

                                    NavigationDrawerItem(

                                        label = {
                                            Text(
                                                chat.title,
                                                maxLines = 1
                                            )
                                        },

                                        selected =
                                            currentChatId ==
                                                    chat.id,

                                        onClick = {

                                            currentChatId =
                                                chat.id

                                            messages =
                                                chat.messages

                                            drawerOpen = false

                                            createSession()

                                            lifecycleScope.launch(
                                                Dispatchers.Default
                                            ) {

                                                chat.messages
                                                    .filter {
                                                        it.fromUser ||
                                                                !it.fromUser
                                                    }
                                                    .takeLast(12)
                                                    .forEach { message ->

                                                        val text =
                                                            if (message.fromUser)
                                                                "User: ${message.text}"
                                                            else
                                                                "TODO: ${message.text}"

                                                        try {
                                                            session?.addQueryChunk(
                                                                text
                                                            )
                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                            }
                                        },

                                        modifier =
                                            Modifier.padding(
                                                horizontal = 12.dp
                                            )
                                    )
                                }
                        }
                    }
                }
            ) {

                Surface(
                    modifier =
                        Modifier.fillMaxSize()
                ) {

                    Column(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {

                        // TOP BAR

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 14.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            TextButton(
                                onClick = {
                                    drawerOpen = true
                                }
                            ) {

                                Text(
                                    "☰",
                                    fontSize = 25.sp
                                )
                            }

                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Text(
                                    "TODO",
                                    fontSize = 27.sp,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    if (modelReady)
                                    
