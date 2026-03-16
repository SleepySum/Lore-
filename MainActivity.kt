package com.example.picsoln

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

// --- Playfair Font Definition ---
// Ensure playfair_display.ttf is in your app/src/main/res/font/ folder
val PlayfairFontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Bold)
)

data class LoreState(
    val pCount: Int = 0,
    val uCount: Int = 0,
    val pThumb: String = "",
    val uThumb: String = "",
    val timestamp: Long = 0L
)

const val LAPTOP_IP = "10.69.116.146"
const val BASE_URL = "http://$LAPTOP_IP:8080"
val LoreGold = Color(0xFFFFDF00)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LoreAppController() } }
    }
}

@Composable
fun LoreAppController() {
    var screen by remember { mutableStateOf("home") }
    var folderName by remember { mutableStateOf("") }
    var loreState by remember { mutableStateOf(LoreState()) }

    LaunchedEffect(Unit) {
        val res = fetchServerData("$BASE_URL/status")
        res?.let {
            loreState = LoreState(
                it.optInt("people"),
                it.optInt("unnamed"),
                it.optString("people_thumb"),
                it.optString("unnamed_thumb"),
                it.optLong("ts")
            )
        }
    }

    BackHandler(enabled = screen == "gallery") { screen = "home" }

    if (screen == "home") {
        HomeScreen(
            state = loreState,
            onStateChange = { loreState = it },
            onOpen = { name ->
                folderName = name
                screen = "gallery"
            }
        )
    } else {
        GalleryScreen(folderName = folderName, onBack = { screen = "home" })
    }
}

@Composable
fun HomeScreen(
    state: LoreState,
    onStateChange: (LoreState) -> Unit,
    onOpen: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            Toast.makeText(context, "Lore is processing...", Toast.LENGTH_SHORT).show()
            scope.launch {
                val res = uploadImages(context, uris)
                res?.let {
                    onStateChange(LoreState(
                        it.optInt("people"),
                        it.optInt("unnamed"),
                        it.optString("people_thumb"),
                        it.optString("unnamed_thumb"),
                        it.optLong("ts")
                    ))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(painter = painterResource(id = R.drawable.app_bg), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(modifier = Modifier.padding(24.dp)) {
            Spacer(modifier = Modifier.height(64.dp))

            // 1. Lore Title with Playfair Font
            Text(
                "Lore",
                color = LoreGold,
                fontSize = 52.sp,
                fontFamily = PlayfairFontFamily,
                fontWeight = FontWeight.Bold
            )

            // 2. Subtitle in Grey, Normal Font
            Text(
                "AI face tracker and sorter",
                color = Color.Gray,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Upload Cluster Button
            Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LoreGold),
                shape = CircleShape
            ) {
                Text("Upload Cluster", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(48.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBox("People", state.pCount, state.pThumb, Modifier.weight(1f)) { onOpen("People") }
                StatBox("Unnamed", state.uCount, state.uThumb, Modifier.weight(1f)) { onOpen("Unnamed") }
            }
        }
    }
}

@Composable
fun GalleryScreen(folderName: String, onBack: () -> Unit) {
    val urls = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val req = Request.Builder().url("$BASE_URL/gallery/$folderName").build()
                    client.newCall(req).execute().use { res ->
                        if (res.isSuccessful) {
                            val arr = JSONArray(res.body?.string() ?: "[]")
                            val l = mutableListOf<String>()
                            for (i in 0 until arr.length()) l.add(arr.getString(i))
                            l
                        } else emptyList()
                    }
                }
                urls.clear()
                urls.addAll(list)
            } catch (e: Exception) { Log.e("Lore", "Err: $e") }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp)) {
        TextButton(onClick = onBack) { Text("< Back", color = LoreGold, fontWeight = FontWeight.Bold) }
        Text(folderName, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(urls) { url ->
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
fun StatBox(label: String, count: Int, base64: String, modifier: Modifier, onClick: () -> Unit) {
    val bitmap = remember(base64) {
        if (base64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) { null }
        } else null
    }
    Surface(modifier = modifier.aspectRatio(0.85f).clickable { onClick() }, shape = RoundedCornerShape(24.dp), color = Color.White.copy(alpha = 0.1f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
        Box {
            if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.4f)
            Column(modifier = Modifier.padding(16.dp).align(Alignment.BottomStart)) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("$count items", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}

suspend fun fetchServerData(url: String): JSONObject? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) JSONObject(res.body?.string() ?: "{}") else null
        }
    } catch (e: Exception) { null }
}

suspend fun uploadImages(context: android.content.Context, uris: List<Uri>): JSONObject? = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        uris.forEach { uri ->
            val b = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (b != null) builder.addFormDataPart("images", "img.jpg", b.toRequestBody("image/jpeg".toMediaType()))
        }
        val req = Request.Builder().url("$BASE_URL/upload").post(builder.build()).build()
        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) JSONObject(res.body?.string() ?: "{}") else null
        }
    } catch (e: Exception) { null }
}