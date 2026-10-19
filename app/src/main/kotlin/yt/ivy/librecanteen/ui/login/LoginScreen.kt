package yt.ivy.librecanteen.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import yt.ivy.librecanteen.R
import yt.ivy.librecanteen.data.AppLanguage
import yt.ivy.librecanteen.data.CanteenRepository
import yt.ivy.librecanteen.ui.UiText
import yt.ivy.librecanteen.ui.asString
import yt.ivy.librecanteen.ui.toUiText

data class LoginState(val loading: Boolean = false, val error: UiText? = null)

class LoginViewModel(private val repository: CanteenRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signIn(address: String, username: String, password: String) {
        if (_state.value.loading) return
        _state.value = LoginState(loading = true)
        viewModelScope.launch {
            try {
                repository.signIn(address, username, password)
                _state.value = LoginState()
            } catch (e: Exception) {
                _state.value = LoginState(error = e.toUiText())
            }
        }
    }
}

private val FieldShape = RoundedCornerShape(16.dp)
private val PanelShape = RoundedCornerShape(24.dp)

// The sign-in form sits on a dark panel in every theme. Dark themes get a dark gray page instead of black.
private val DarkPage = Color(0xFF141312)
private val PanelColor = Color(0xFF1C1B1A)
private val PanelField = Color(0xFF141312)
private val PanelInk = Color(0xFFF2EFEC)
private val PanelError = Color(0xFFF2B8B5)

// Kept free above and below the content, where the top bar and the footer sit, so the content is centered on the page.
private val PageEdge = 96.dp

private const val STEP_WELCOME = 0
private const val STEP_FORM = 1

private const val AUTHOR_URL = "https://ivy.yt"
private const val PRIVACY_URL = "https://privacypolicy.librecanteen.ivy.yt"

@Composable
fun LoginScreen(
    state: LoginState,
    onLanguage: (AppLanguage) -> Unit,
    onSignIn: (address: String, username: String, password: String) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(STEP_WELCOME) }
    var address by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    // Deliberately not saveable: the password must not end up in saved instance state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val canSubmit = address.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !state.loading
    val submit = { if (canSubmit) onSignIn(address, username, password) }
    val background = MaterialTheme.colorScheme.background
    val page = if (background.luminance() < 0.5f) DarkPage else background
    val ink = MaterialTheme.colorScheme.onBackground

    BackHandler(enabled = step == STEP_FORM) { step = STEP_WELCOME }

    Surface(color = page, contentColor = ink, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = PageEdge)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (step == STEP_WELCOME) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(220.dp))
                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.login_tagline), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = { step = STEP_FORM },
                        shape = FieldShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = page),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.login_button), style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    CompositionLocalProvider(LocalContentColor provides PanelInk) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(PanelShape).background(PanelColor).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(stringResource(R.string.login_url_title), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.login_url_text), style = MaterialTheme.typography.bodyMedium)
                            LoginTextField(
                                value = address,
                                onValueChange = { address = it },
                                placeholder = stringResource(R.string.login_address_hint),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.login_username), style = MaterialTheme.typography.titleLarge)
                            LoginTextField(
                                value = username,
                                onValueChange = { username = it },
                                placeholder = stringResource(R.string.login_username_hint),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.login_password), style = MaterialTheme.typography.titleLarge)
                            LoginTextField(
                                value = password,
                                onValueChange = { password = it },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                trailing = {
                                    if (state.loading) {
                                        CircularProgressIndicator(Modifier.padding(end = 12.dp).size(24.dp), color = PanelInk, strokeWidth = 2.dp)
                                    } else {
                                        ArrowButton(stringResource(R.string.login_button), canSubmit) { submit() }
                                    }
                                },
                            )
                            Text(
                                stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable(role = Role.Switch) { showPassword = !showPassword }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                            )
                            state.error?.let { Text(it.asString(), color = PanelError, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            if (step == STEP_FORM) {
                Row(
                    Modifier.align(Alignment.TopCenter).widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { step = STEP_WELCOME }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = ink,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            LoginFooter(onLanguage, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val hint = PanelInk.copy(alpha = 0.6f)
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        trailingIcon = trailing,
        singleLine = true,
        shape = FieldShape,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = PanelField,
            unfocusedContainerColor = PanelField,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = PanelInk,
            unfocusedTextColor = PanelInk,
            cursorColor = PanelInk,
            focusedPlaceholderColor = hint,
            unfocusedPlaceholderColor = hint,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ArrowButton(description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = description,
            tint = if (enabled) PanelInk else PanelInk.copy(alpha = 0.38f),
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun LoginFooter(onLanguage: (AppLanguage) -> Unit, modifier: Modifier) {
    val uriHandler = LocalUriHandler.current
    val open = { url: String -> runCatching { uriHandler.openUri(url) } }
    val english = LocalConfiguration.current.locales[0].language == "en"
    val small = MaterialTheme.typography.bodySmall
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.login_created_by), style = MaterialTheme.typography.bodyLarge)
            FooterLink("ivy.yt", MaterialTheme.typography.bodyLarge, underlined = true) { open(AUTHOR_URL) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FooterLink(stringResource(R.string.login_privacy_policy), small, underlined = false) { open(PRIVACY_URL) }
            Text("•", style = small, modifier = Modifier.padding(horizontal = 8.dp))
            LanguageSwitch(english, onLanguage)
        }
    }
}

/** One text button for both languages: the active one is underlined, and a tap switches to the other. */
@Composable
private fun LanguageSwitch(english: Boolean, onLanguage: (AppLanguage) -> Unit) {
    val czech = stringResource(R.string.login_language_czech)
    val englishName = stringResource(R.string.login_language_english)
    val label = buildAnnotatedString {
        withStyle(SpanStyle(textDecoration = if (english) TextDecoration.None else TextDecoration.Underline)) { append(czech) }
        append(" / ")
        withStyle(SpanStyle(textDecoration = if (english) TextDecoration.Underline else TextDecoration.None)) { append(englishName) }
    }
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .toggleable(value = english, role = Role.Switch) { checked ->
                onLanguage(if (checked) AppLanguage.ENGLISH else AppLanguage.CZECH)
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun FooterLink(text: String, style: TextStyle, underlined: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = style,
        textDecoration = if (underlined) TextDecoration.Underline else null,
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
}
