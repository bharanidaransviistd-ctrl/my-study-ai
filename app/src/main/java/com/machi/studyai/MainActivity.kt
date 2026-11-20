package com.machi.studyai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StudyCoderChatScreen(context = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyCoderChatScreen(context: ComponentActivity) {
    val scope = rememberCoroutineScope()
    val localModelFile = remember { File(context.filesDir, "model.task") }

    var llmInference by remember { mutableStateOf<LlmInference?>(null) }
    var statusText by remember { mutableStateOf("Tap 'Pick Model' to load a .task file") }
    var isModelReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var userInput by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }

    fun loadModel() {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            withContext(Dispatchers.Main) { statusText = "Loading model into RAM..." }
            try {
                llmInference?.close()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(localModelFile.absolutePath)
                    .setMaxTokens(1024)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                withContext(Dispatchers.Main) {
                    isModelReady = true
                    statusText = "Offline AI Ready (S23)"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isModelReady = false
                    statusText = "Load error: ${e.localizedMessage}"
                }
            } finally {
                isLoading = false
            }
        }
    }

    // App open aanadhum already copy panna model irundha auto-load pannum
    LaunchedEffect(Unit) {
        if (localModelFile.exists() && localModelFile.length() > 0) {
            loadModel()
        }
    }

    // Phone Downloads-la irundhu .task file select panna file picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                isLoading = true
                withContext(Dispatchers.Main) {
                    statusText = "Copying model to app storage (wait ~15s)..."
                }
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localModelFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    loadModel()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusText = "Copy error: ${e.localizedMessage}"
                        isLoading = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Study AI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(statusText, fontSize = 12.sp, color = Color.Gray)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !isLoading && !isGenerating
                    ) {
                        Text("Pick Model")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Text(
                            text = msg.text,
                            color = if (msg.isUser) Color.White else Color.Black,
                            modifier = Modifier
                                .background(
                                    color = if (msg.isUser) Color(0xFF1A73E8) else Color(0xFFE8EAED),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                                .widthIn(max = 300.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask study doubt or code...") },
                    enabled = isModelReady && !isGenerating
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val query = userInput.trim()
                        if (query.isNotEmpty() && llmInference != null) {
                            userInput = ""
                            messages.add(ChatMessage(query, isUser = true))
                            messages.add(ChatMessage("Thinking...", isUser = false))
                            val aiIndex = messages.lastIndex
                            isGenerating = true

                            val fullPrompt = """
                                You are an offline AI tutor for a Computer Science student.
                                Explain concepts clearly in bullet points and give clean, bug-free code when asked.
                                
                                Question: $query
                                Answer:
                            """.trimIndent()

                            scope.launch(Dispatchers.IO) {
                                var reply = ""
                                try {
                                    llmInference?.generateResponseAsync(fullPrompt) { partial, done ->
                                        reply += partial
                                        scope.launch(Dispatchers.Main) {
                                            messages[aiIndex] = ChatMessage(reply, isUser = false)
                                            if (done) isGenerating = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        messages[aiIndex] = ChatMessage("Error: ${e.localizedMessage}", isUser = false)
                                        isGenerating = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = isModelReady && !isGenerating && userInput.isNotBlank()
                ) {
                    Text(if (isGenerating) "..." else "Send")
                }
            }
        }
    }
}
