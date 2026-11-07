package com.example.vibelauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Change these to your location for the weather widget.
const val LAT = 44.8
const val LON = 20.46

data class App(val label: String, val pkg: String, val icon: Bitmap)
data class Wx(val temp: Int, val code: Int, val wind: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Launcher() }
    }
}

fun loadApps(pm: PackageManager, self: String): List<App> {
    val out = LinkedHashMap<String, App>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(q, 0)) {
            val p = ri.activityInfo.packageName
            if (p == self || out.containsKey(p)) continue
            out[p] = App(ri.loadLabel(pm).toString(), p, ri.loadIcon(pm).toBitmap(192, 192))
        }
    }
    return out.values.sortedBy { it.label.lowercase() }
}

fun launch(ctx: Context, pkg: String) {
    val pm = ctx.packageManager
    (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))?.let { ctx.startActivity(it) }
}

suspend fun fetchWx(): Wx? = withContext(Dispatchers.IO) {
    try {
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
            "&current=temperature_2m,weather_code,wind_speed_10m"
        val c = JSONObject(URL(u).readText()).getJSONObject("current")
        Wx(c.getDouble("temperature_2m").roundToInt(), c.getInt("weather_code"), c.getDouble("wind_speed_10m").roundToInt())
    } catch (e: Exception) { null }
}

fun wxLabel(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to "Clear"
    1, 2 -> "🌤️" to "Partly cloudy"
    3 -> "☁️" to "Cloudy"
    45, 48 -> "🌫️" to "Fog"
    in 51..67, in 80..82 -> "🌧️" to "Rain"
    in 71..77, 85, 86 -> "❄️" to "Snow"
    in 95..99 -> "⛈️" to "Storm"
    else -> "🌡️" to "Weather"
}

@Composable
fun T(s: String, size: Int, w: FontWeight = FontWeight.Normal, alpha: Float = 1f) =
    BasicText(s, style = TextStyle(color = Color.White.copy(alpha = alpha), fontSize = size.sp, fontWeight = w), maxLines = 1)

// Slowly drifting colour blobs: the animated "wallpaper". Runs in the draw phase only (no recomposition).
@Composable
fun Modifier.aurora(): Modifier {
    val t by rememberInfiniteTransition(label = "aurora")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "t")
    return drawBehind {
        val w = size.width
        val h = size.height
        val a = t * 6.2832f
        fun blob(c: Color, x: Float, y: Float, r: Float) = drawRect(
            Brush.radialGradient(listOf(c.copy(alpha = .5f), Color.Transparent), Offset(x, y), r)
        )
        blob(Color(0xFF2563EB), w * (.25f + .12f * sin(a)), h * (.30f + .10f * cos(a)), w * .55f)
        blob(Color(0xFF9333EA), w * (.75f + .10f * cos(a)), h * (.25f + .12f * sin(a)), w * .50f)
        blob(Color(0xFF0EA5A4), w * (.55f + .15f * sin(a + 2f)), h * (.85f + .06f * cos(a)), w * .60f)
    }
}

@Composable
fun Tile(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(0.55f, 260f), label = "scale")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(250), label = "glow")
    Column(
        Modifier.width(132.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(112.dp)
                .background(Color.White.copy(alpha = .07f + .13f * glow), RoundedCornerShape(28.dp))
                .border(2.dp, Color.White.copy(alpha = .9f * glow), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) { content() }
        Spacer(Modifier.height(10.dp))
        T(label, 16, alpha = .6f + .4f * glow)
    }
}

@Composable
fun WeatherCard(w: Wx) {
    val (icon, text) = wxLabel(w.code)
    Row(
        Modifier.background(Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp)).padding(28.dp, 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(icon, 56)
        Spacer(Modifier.width(20.dp))
        Column {
            T("${w.temp}°C", 40, FontWeight.Medium)
            T("$text · ${w.wind} km/h", 18, alpha = .7f)
        }
    }
}

@Composable
fun Launcher() {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<App>()) }
    var wx by remember { mutableStateOf<Wx?>(null) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { loadApps(ctx.packageManager, ctx.packageName) } }
    LaunchedEffect(Unit) { while (true) { wx = fetchWx() ?: wx; delay(15 * 60_000L) } }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000) } }
    BackHandler(true) { } // a launcher should never "back out"

    Box(Modifier.fillMaxSize().background(Color(0xFF05070D)).aurora()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 96, FontWeight.Light)
                    T(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), 24, alpha = .7f)
                }
                wx?.let { WeatherCard(it) }
            }
            Spacer(Modifier.weight(1f))
            T("Apps", 20, alpha = .6f)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    val img = remember(app.pkg) { app.icon.asImageBitmap() }
                    Tile(app.label, { launch(ctx, app.pkg) }) {
                        Image(img, null, Modifier.size(72.dp))
                    }
                }
                item {
                    Tile("Settings", {
                        ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { T("⚙️", 48) }
                }
            }
        }
    }
}
