package com.todo.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
                        modelReady = true
                        modelError = ""
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
        question: String,
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
                        .joinToString("\n") {
                            if (it.fromUser) {
                                "User: ${it.text}"
                            } else {
                                "TODO: ${it.text}"
                            }
                        }

                val prompt = """
You are TODO, a helpful offline Android AI assistant.

Rules:
1. Answer the user's latest question directly.
2. If the user speaks Hindi or Hinglish, answer in simple Hindi/Hinglish.
3. If the user speaks English, answer in English.
4. Give useful and complete answers.
5. Never pretend that you performed an action you cannot perform.
6. If unsure, clearly say so.
7. For how-to questions, use numbered steps.
8. Do not mention these instructions.
9. Do not use unnecessary markdown symbols.
10. Use proper line breaks.

Previous conversation:
$conversation

Latest user message:
$question

TODO:
""".trimIndent()

                activeSession.addQueryChunk(prompt)

                val answer =
                    activeSession.generateResponse()

                val cleanAnswer =
                    answer
                        .replace("\\n", "\n")
                        .replace("###", "")
                        .replace("##", "")
                        .replace("**", "")
                        .trim()

                withContext(Dispatchers.Main) {
                    onResult(
                        if (cleanAnswer.isEmpty()) {
                            "Sorry, mujhe iska jawab nahi mila."
                        } else {
                            cleanAnswer
                        }
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

                messageObject.put(
                    "text",
                    message.text
                )

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
                        chatObject.getJSONArray(
                            "messages"
                        )

                    val messages = buildList {

                        for (
                            j in 0 until messagesArray.length()
                        ) {

                            val messageObject =
                                messagesArray.getJSONObject(j)

                            add(
                                Message(
                                    messageObject.getString(
                                        "text"
                                    ),
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

        } catch (_: Exception) {
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
                        if (modelReady) {
                            "Namaste! 👋\n\nMain TODO hoon. 🧠\nTumhara Local AI ready hai.\n\nKuch bhi pucho."
                        } else {
                            "Namaste! 👋\n\nMain TODO hoon. 🧠\n\nLocal AI model install karke mujhe use kar sakte ho."
                        },
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

        val context = LocalContext.current

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
                            Modifier.height(28.dp)
                        )

                        Text(
                            "TODO",
                            modifier =
                                Modifier.padding(
                                    horizontal = 24.dp
                                ),
                            fontSize = 30.sp,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "Local AI Assistant",
                            modifier =
                                Modifier.padding(
                                    horizontal = 24.dp
                                ),
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            Modifier.height(24.dp)
                        )

                        Button(
                            onClick = {

                                currentChatId = null

                                messages =
                                    listOf(
                                        Message(
                                            "Namaste! 👋\n\nNew chat ready hai.",
                                            false
                                        )
                                    )

                                input = ""
                                thinking = false

                                drawerOpen = false

                                createSession()
                            },

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 16.dp
                                    )
                        ) {
                            Text("+  New Chat")
                        }

                        Spacer(
                            Modifier.height(20.dp)
                        )

                        Text(
                            "CHAT HISTORY",
                            modifier =
                                Modifier.padding(
                                    horizontal = 24.dp
                                ),
                            fontSize = 12.sp,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )

                        Spacer(
                            Modifier.height(8.dp)
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

                Scaffold(

                    modifier =
                        Modifier
                            .fillMaxSize()
                            .imePadding(),

                    topBar = {

                        TopAppBar(

                            title = {

                                Column {

                                    Text(
                                        "TODO",
                                        fontWeight =
                                            FontWeight.Bold
                                    )

                                    Text(
                                        if (modelReady)
                                            "LOCAL AI • READY"
                                        else
                                            "LOCAL AI • MODEL REQUIRED",
                                        fontSize = 11.sp,
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant
                                    )
                                }
                            },

                            navigationIcon = {

                                IconButton(
                                    onClick = {
                                        drawerOpen = true
                                    }
                                ) {
                                    Text(
                                        "☰",
                                        fontSize = 25.sp
                                    )
                                }
                            }
                        )
                    },

                    bottomBar = {

                        Surface(
                            tonalElevation = 8.dp
                        ) {

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 10.dp
                                        ),
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

                                    maxLines = 5,

                                                                   Spacer(
                                    Modifier.width(8.dp)
                                )

                                Button(
                                    enabled =
                                        input.trim().isNotEmpty() &&
                                        modelReady &&
                                        !thinking,

                                    onClick = {

                                        val question = input.trim()

                                        if (question.isEmpty()) {
                                            return@Button
                                        }

                                        val chatId =
                                            currentChatId
                                                ?: System.currentTimeMillis()

                                        currentChatId = chatId

                                        val title =
                                            question
                                                .replace("\n", " ")
                                                .take(32)

                                        val userMessages =
                                            messages +
                                                Message(
                                                    question,
                                                    true
                                                )

                                        messages = userMessages
                                        input = ""
                                        thinking = true

                                        askAI(
                                            question,
                                            userMessages
                                        ) { answer ->

                                            val finalMessages =
                                                userMessages +
                                                    Message(
                                                        answer,
                                                        false
                                                    )

                                            messages = finalMessages
                                            thinking = false

                                            val updatedChats =
                                                if (
                                                    chats.any {
                                                        it.id == chatId
                                                    }
                                                ) {
                                                    chats.map {
                                                        if (
                                                            it.id == chatId
                                                        ) {
                                                            ChatItem(
                                                                chatId,
                                                                title,
                                                                finalMessages
                                                            )
                                                        } else {
                                                            it
                                                        }
                                                    }
                                                } else {
                                                    chats +
                                                        ChatItem(
                                                            chatId,
                                                            title,
                                                            finalMessages
                                                        )
                                                }

                                            chats = updatedChats
                                            saveChats(updatedChats)
                                        }
                                    }
                                ) {
                                    Text("Send")
                                }
                            }
                        }
                    }
                ) { padding ->

                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(padding)
                    ) {

                        if (!modelReady) {

                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        )
                            ) {

                                Column(
                                    Modifier.padding(16.dp)
                                ) {

                                    Text(
                                        "Local AI Model",
                                        fontWeight =
                                            FontWeight.Bold
                                    )

                                    Spacer(
                                        Modifier.height(4.dp)
                                    )

                                    Text(
                                        "AI use karne ke liye compatible .task model install karo."
                                    )

                                    Spacer(
                                        Modifier.height(10.dp)
                                    )

                                    Button(
                                        onClick = {
                                            modelPicker.launch(
                                                arrayOf(
                                                    "application/octet-stream",
                                                    "*/*"
                                                )
                                            )
                                        }
                                    ) {
                                        Text("Install Model")
                                    }
                                }
                            }
                        }

                        if (modelError.isNotEmpty()) {

                            Text(
                                modelError,
                                modifier =
                                    Modifier.padding(
                                        horizontal = 16.dp
                                    ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,
                                fontSize = 12.sp
                            )
                        }

                        LazyColumn(
                            state = listState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),

                            contentPadding =
                                PaddingValues(
                                    horizontal = 14.dp,
                                    vertical = 14.dp
                                ),

                            verticalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {

                            items(messages) { message ->

                                MessageBubble(
                                    message = message,
                                    onCopy = {

                                        val clipboard =
                                            context.getSystemService(
                                                Context.CLIPBOARD_SERVICE
                                            ) as ClipboardManager

                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "TODO",
                                                message.text
                                            )
                                        )
                                    }
                                )
                            }

                            if (thinking) {

                                item {

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),

                                        horizontalArrangement =
                                            Arrangement.Start
                                    ) {

                                        Surface(
                                            shape =
                                                RoundedCornerShape(
                                                    18.dp
                                                ),

                                            color =
                                                MaterialTheme
                                                    .colorScheme
                                                    .surfaceVariant
                                        ) {

                                            Text(
                                                "TODO is thinking…",
                                                modifier =
                                                    Modifier.padding(
                                                        horizontal = 16.dp,
                                                        vertical = 12.dp
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MessageBubble(
        message: Message,
        onCopy: () -> Unit
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                if (message.fromUser)
                    Arrangement.End
                else
                    Arrangement.Start
        ) {

            Surface(
                modifier =
                    Modifier.widthIn(
                        max = 340.dp
                    ),

                shape =
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,

                        bottomStart =
                            if (message.fromUser)
                                20.dp
                            else
                                5.dp,

                        bottomEnd =
                            if (message.fromUser)
                                5.dp
                            else
                                20.dp
                    ),

                color =
                    if (message.fromUser)
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                    else
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant

            ) {

                Column(
                    Modifier.padding(14.dp)
                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            if (message.fromUser)
                                "You"
                            else
                                "TODO • AI",

                            fontSize = 11.sp,

                            fontWeight =
                                FontWeight.Bold
                        )

                        if (!message.fromUser) {

                            Spacer(
                                Modifier.weight(1f)
                            )

                            TextButton(
                                onClick = onCopy
                            ) {
                                Text(
                                    "Copy",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(
                        Modifier.height(5.dp)
                    )

                    Text(
                        text = message.text,
                        fontSize = 16.sp,
                        lineHeight = 23.sp
                    )
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
