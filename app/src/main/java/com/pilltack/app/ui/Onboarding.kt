package com.pilltack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pilltack.app.data.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(nav: NavHostController, repo: Repo) {
    LaunchedEffect(Unit) {
        delay(900)
        val done = repo.prefs.first().onboarded
        nav.navigate(if (done) "home" else "onboarding") { popUpTo("splash") { inclusive = true } }
    }
    Box(Modifier.fillMaxSize().background(PT.Blue), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillGlyph(0, Color(0xFF6FA8F5), Color(0xFFE3E6EA), Color.Transparent, 44.dp)
            Spacer(Modifier.width(8.dp))
            Txt("PillTack", T.h1, Color.White)
        }
    }
}

@Composable
fun OnboardingScreen(nav: NavHostController, repo: Repo) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(PT.Blue).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp)); Row { Txt("PillTack", T.b1, Color.White) }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillGlyph(0, Color(0xFFE5383B), Color.White, Color(0xFFF5A623), 76.dp)
            PillGlyph(2, Color(0xFF6EC1F5), Color.White, Color(0xFFF29BD0), 76.dp)
            PillGlyph(6, Color(0xFF8FD18B), Color(0xFF3D7BE0), Color(0xFFF5E6B8), 76.dp)
        }
        Spacer(Modifier.height(32.dp))
        Txt("\u201CStart your health journey with us.\u201D", T.h2, Color.White, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt("\u201CTogether, we can make every day a step towards a healthier you.\u201D", T.b3, Color.White.copy(alpha = 0.85f), align = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White)); Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        }
        Spacer(Modifier.height(16.dp))
        Button({ scope.launch { repo.setOnboarded(); nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } } },
            Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = PT.Blue)) { Txt("Get Started", T.b1, PT.Blue) }
        Spacer(Modifier.height(16.dp))
    }
}
