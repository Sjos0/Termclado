package com.example

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.widget.Toast
import android.content.pm.PackageManager
import android.Manifest
import com.example.keyboard.KeyboardPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.keyboard.ScreenshotSource

class MainActivity : Activity() {

    private lateinit var prefs: KeyboardPreferences
    private val density by lazy { resources.displayMetrics.density }
    private fun dp(value: Int) = (value * density).toInt()

    private val REQUEST_MIC = 1001
    private val REQUEST_SHOTS = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = KeyboardPreferences(this)

        if (intent?.getBooleanExtra("request_mic_permission", false) == true) {
            requestMicPermission()
        }

        // Paleta alinhada ao visual de configurações do Gboard (Material claro)
        val bgPage = Color.parseColor("#F8F9FA")
        val bgBar = Color.parseColor("#FFFFFF")
        val textPrimary = Color.parseColor("#202124")
        val textSecondary = Color.parseColor("#5F6368")
        val dividerColor = Color.parseColor("#E8EAED")
        val iconTint = Color.parseColor("#5F6368")
        val accentBlue = Color.parseColor("#1A73E8")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgPage)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // App bar estilo Gboard: nome do app + sem botão voltar exagerado
        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bgBar)
            setPadding(dp(20), dp(18), dp(16), dp(18))
            elevation = 2f * density
        }

        val headerTitle = TextView(this).apply {
            text = "Termclado"
            textSize = 22f
            setTextColor(textPrimary)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        appBar.addView(createHeaderLogo())
        appBar.addView(headerTitle)
        root.addView(appBar)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(28))
        }

        // Ordem alinhada ao Gboard: Idiomas → Preferências → Tema → Correção →
        // Dicionário → Digitação por voz → Privacidade → Sobre (+ atualizações)
        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_keyboard,
            title = "Capturas de tela na barra",
            subtitle = if (ScreenshotSource.hasPermission(this)) "Permitido: a captura mais recente aparece na barra"
                else "Permitir para mostrar a captura mais recente na barra"
        ) { requestShotsPermission() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_language,
            title = "Idiomas",
            subtitle = "Português (Brasil)"
        ) { showLanguageDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_preferences,
            title = "Preferências",
            subtitle = "Vibração, layout e atalhos"
        ) { showPreferencesDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_theme,
            title = "Tema",
            subtitle = themeSubtitle()
        ) { showThemeDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_correction,
            title = "Correção de texto",
            subtitle = "Autocorreção e capitalização"
        ) { showCorrectionsDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_keyboard,
            title = "Símbolos e Layout",
            subtitle = "Linha numérica, ?123 e atalhos"
        ) { showSymbolsDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_dictionary,
            title = "Dicionário",
            subtitle = "Dicionário offline pt-BR"
        ) { showDictionaryDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_voice,
            title = "Digitação por voz",
            subtitle = if (prefs.groqApiKey.isNotBlank()) "Groq Whisper configurado" else "Configure a chave da API Groq"
        ) { showSpeechToTextDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_privacy,
            title = "Privacidade",
            subtitle = "Processamento local e dados"
        ) { showPrivacyDialog() })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_update,
            title = "Verificar atualizações",
            subtitle = "Versão ${BuildConfig.VERSION_NAME}"
        ) { checkAppUpdates(showFeedbackIfLatest = true) })

        listContainer.addView(createSettingsItem(
            iconRes = R.drawable.ic_settings_info,
            title = "Sobre",
            subtitle = "Termclado v${BuildConfig.VERSION_NAME}"
        ) { showAboutDialog() })

        scrollView.addView(listContainer)
        root.addView(scrollView)
        setContentView(root)

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2001)
        }

        // Busca automática silenciosa por novas atualizações no GitHub Releases
        CoroutineScope(Dispatchers.Main).launch {
            checkAppUpdates(showFeedbackIfLatest = false)
        }
    }

    private fun createHeaderLogo(): View {
        val size = dp(40)
        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.parseColor("#E8EAED"))
            }
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(12)
            }
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_keyboard_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            layoutParams = FrameLayout.LayoutParams(size, size)
        }
        circle.addView(logo)
        return circle
    }

    private fun themeSubtitle(): String {
        val id = try { prefs.themeId } catch (_: Exception) { 0 }
        return when (id) {
            1 -> "OLED Preto"
            2 -> "Ardósia Azul"
            else -> "Grafite e Cobalto"
        }
    }

    private fun createSettingsItem(
        iconRes: Int,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#FFFFFF"))
            setPadding(dp(20), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
            // ripple sobre fundo branco
            foreground = getDrawable(typedValue.resourceId)
            onTap { onClick() }
            minimumHeight = dp(64)
        }

        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.parseColor("#5F6368"))
            val iconSize = dp(24)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = dp(20)
            }
        }
        row.addView(icon)

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.parseColor("#202124"))
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        textContainer.addView(titleView)

        if (subtitle.isNotBlank()) {
            val subtitleView = TextView(this).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#5F6368"))
                setPadding(0, dp(2), 0, 0)
            }
            textContainer.addView(subtitleView)
        }

        row.addView(textContainer)

        // Chevron Material (mesmo padrão visual do Gboard)
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings_chevron)
            val s = dp(24)
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        row.addView(chevron)
        return row
    }

    private fun showLanguageDialog() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        AlertDialog.Builder(this)
            .setTitle("Idiomas e Entrada")
            .setMessage("Layout atual: Português (Brasil) (QWERTY)\n\n• Inclui linha numérica dedicada (1 a 0).\n• Tecla 'ç' visível diretamente na linha principal.\n• Acentos completos (á, à, â, ã, é, ê, í, ó, ô, õ, ú) no toque prolongado.")
            .setPositiveButton("Alterar Teclado Ativo") { _, _ ->
                imm.showInputMethodPicker()
            }
            .setNeutralButton("Configurações do Sistema") { _, _ ->
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showPreferencesDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val switchVibrate = Switch(this).apply {
            text = "Vibração ao tocar teclas"
            setTextColor(Color.parseColor("#202124"))
            textSize = 16f
            isChecked = prefs.vibrateOnPress
            setOnCheckedChangeListener { _, isChecked ->
                prefs.vibrateOnPress = isChecked
            }
        }
        val vibrateSub = TextView(this).apply {
            text = "Resposta tátil no toque (desativado por padrão para economizar bateria)."
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        }
        container.addView(switchVibrate)
        container.addView(vibrateSub)

        val switchCap = Switch(this).apply {
            text = "Autocapitalização"
            setTextColor(Color.parseColor("#202124"))
            textSize = 16f
            isChecked = prefs.autoCapitalization
            setOnCheckedChangeListener { _, isChecked ->
                prefs.autoCapitalization = isChecked
            }
        }
        val capSub = TextView(this).apply {
            text = "Primeira letra em maiúscula no início de sentenças e após ponto final."
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(8))
        }
        container.addView(switchCap)
        container.addView(capSub)

        AlertDialog.Builder(this)
            .setTitle("Preferências")
            .setView(container)
            .setPositiveButton("Pronto", null)
            .show()
    }

    private fun showThemeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Tema do Teclado")
            .setMessage("Tema Escuro Dinâmico de Alto Contraste\n\n• Fundo grafite profundo (#172227) e teclas bem definidas (#2A373E).\n• Consumo mínimo de energia em telas OLED e LCD.\n• Destaques em azul cobalto para melhor visibilidade e conforto visual.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showCorrectionsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val switchAutoCorrect = Switch(this).apply {
            text = "Autocorreção conservadora"
            setTextColor(Color.parseColor("#202124"))
            textSize = 16f
            isChecked = prefs.autoCorrection
            setOnCheckedChangeListener { _, isChecked ->
                prefs.autoCorrection = isChecked
            }
        }
        val autoCorrectSub = TextView(this).apply {
            text = "Corrige apenas erros nítidos e restaura acentos seguros (ex: 'voce' → 'você', 'nao' → 'não'). Palavras válidas são sempre preservadas."
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 13f
            setPadding(0, dp(2), 0, dp(16))
        }
        container.addView(switchAutoCorrect)
        container.addView(autoCorrectSub)

        AlertDialog.Builder(this)
            .setTitle("Correção de Texto")
            .setView(container)
            .setPositiveButton("Pronto", null)
            .show()
    }

    private fun showSymbolsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Símbolos e Layout")
            .setMessage("Acesso Fácil aos Símbolos\n\n1. Tecla '?123' na linha inferior (canto esquerdo): alterna diretamente para os números e símbolos.\n2. Botão '?123' na barra de ferramentas superior: atalho rápido na barra de sugestões.\n3. Tecla '=\\<': abre mais símbolos matemáticos e especiais (moedas, colchetes, etc.).\n4. Toque longo no ponto (.): abre menu rápido com '!', '?', '@', '-', '_', '/'.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun showDictionaryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Dicionário Offline")
            .setMessage("Dicionário de Alta Eficiência PT-BR\n\n• 100% offline e armazenado localmente no aplicativo.\n• Formato binário ultra-compacto indexado por buckets O(1).\n• Baseado no vocabulário oficial pt-BR do Projeto Vero (LGPLv3/MPL).\n• Carregamento instantâneo via Rust nativo / Kotlin puro.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(this)
            .setTitle("Privacidade e Segurança")
            .setMessage("Privacidade\n\n• O teclado não grava histórico. Nenhum texto digitado é registrado ou compartilhado.\n• A digitação por voz é opcional: ela só funciona se você configurar sua chave da Groq, e nesse caso o áudio gravado é enviado à Groq para transcrição.\n• A verificação de atualizações acessa o GitHub Releases, no máximo a cada 12 horas enquanto você usa o teclado, ou quando você toca em verificar.\n• Capturas de tela: só se você conceder a permissão de fotos. O teclado lê apenas a captura mais recente (nome e data) para mostrar na barra. A imagem só vai ao app que você está usando, e só quando você toca na pílula. Nada sai do aparelho por essa via.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sobre o Termclado")
            .setMessage("Termclado v${BuildConfig.VERSION_NAME}\n\n• Desenvolvido para celulares com menos de 1 GB de RAM e 16 GB de armazenamento.\n• Consumo PSS real < 10 MB de RAM.\n• Algoritmo de tolerância a toque rápido para 0ms de latência.\n• Compatível com Android 8.0 (API 26) ou superior.")
            .setPositiveButton("Fechar", null)
            .show()
    }

    private fun checkAppUpdates(showFeedbackIfLatest: Boolean) {
        val progressDialog = if (showFeedbackIfLatest) {
            AlertDialog.Builder(this)
                .setTitle("Verificando Atualizações")
                .setMessage("Consultando GitHub Releases...")
                .setCancelable(false)
                .create().apply { show() }
        } else null

        CoroutineScope(Dispatchers.Main).launch {
            val result = UpdateChecker.checkForUpdates()
            progressDialog?.dismiss()

            when (result) {
                is UpdateChecker.UpdateResult.NewVersion -> {
                    showUpdateAvailableDialog(result)
                }
                is UpdateChecker.UpdateResult.UpToDate -> {
                    if (showFeedbackIfLatest) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Aplicativo Atualizado")
                            .setMessage("Você já está na versão mais recente (v${result.currentVersion}).")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
                is UpdateChecker.UpdateResult.Error -> {
                    if (showFeedbackIfLatest) {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Status das Atualizações")
                            .setMessage("Versão instalada: v${BuildConfig.VERSION_NAME}\n\nStatus: ${result.message}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
    }

    private var downloadCancelled = false

    private fun startInAppUpdate(result: UpdateChecker.UpdateResult.NewVersion) {
        val apkUrl = result.apkUrl
        if (apkUrl == null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl)))
            } catch (_: Exception) {
                Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (!ApkInstaller.canInstallPackages(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permitir instalação")
                .setMessage("Para instalar a atualização, ative a opção de permitir instalação para o Termclado. Depois volte aqui e toque em baixar de novo.")
                .setPositiveButton("Abrir configurações") { _, _ ->
                    try {
                        startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
                .setNegativeButton("Agora não", null)
                .show()
            return
        }
        showDownloadProgress(result, apkUrl)
    }

    private fun showDownloadProgress(result: UpdateChecker.UpdateResult.NewVersion, apkUrl: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(4))
        }
        val status = TextView(this).apply {
            text = "Conectando ao GitHub…"
            textSize = 14f
            setTextColor(Color.parseColor("#5F6368"))
        }
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        box.addView(status)
        box.addView(bar)

        downloadCancelled = false
        val dialog = AlertDialog.Builder(this)
            .setTitle("Baixando v${result.latestVersion}")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("Cancelar") { _, _ -> downloadCancelled = true }
            .create()
        dialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            val appContext = applicationContext
            try {
                val file = ApkDownloader.download(
                    context = appContext,
                    url = apkUrl,
                    version = result.latestVersion,
                    isCancelled = { downloadCancelled },
                    onProgress = { done, total ->
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        val text = if (total > 0) {
                            "${mbText(done)} de ${mbText(total)} ($pct%)"
                        } else {
                            "${mbText(done)} baixados"
                        }
                        runOnUiThread {
                            bar.progress = pct
                            status.text = text
                        }
                    }
                )
                val problem = ApkVerifier.verify(appContext, file, BuildConfig.VERSION_CODE)
                if (problem != null) {
                    ApkDownloader.deleteAll(appContext)
                    throw IllegalStateException(problem)
                }
                runOnUiThread { if (dialog.isShowing) dialog.dismiss() }
                ApkInstaller.install(appContext, file)
            } catch (_: ApkDownloader.CancelledException) {
                ApkDownloader.deleteAll(appContext)
                runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                    Toast.makeText(
                        this@MainActivity,
                        "Download cancelado. Toque em baixar de novo para continuar.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                ApkDownloader.deleteAll(appContext)
                runOnUiThread {
                    if (dialog.isShowing) dialog.dismiss()
                    showDownloadError(result, e.message ?: "erro desconhecido")
                }
            }
        }
    }

    private fun showDownloadError(result: UpdateChecker.UpdateResult.NewVersion, message: String) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("Não foi possível atualizar")
            .setMessage("$message\n\nNada ficou salvo no celular. Toque em \"Tentar de novo\" para baixar outra vez.")
            .setPositiveButton("Tentar de novo") { _, _ -> startInAppUpdate(result) }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun mbText(bytes: Long): String = String.format("%.1f MB", bytes / 1048576.0)

    private fun showUpdateAvailableDialog(result: UpdateChecker.UpdateResult.NewVersion) {
        // Altura máxima do conteúdo rolável: ~50% da tela, para os 3 botões
        // do AlertDialog (Baixar / GitHub / Depois) ficarem sempre visíveis.
        val screenH = resources.displayMetrics.heightPixels
        val maxScrollH = (screenH * 0.50f).toInt().coerceIn(dp(160), dp(420))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(6), dp(18), dp(2))
        }

        val versionInfo = TextView(this).apply {
            text = "Versão atual instalada: v${result.currentVersion}\nVersão mais recente: v${result.latestVersion}"
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 13.5f
            setLineSpacing(0f, 1.2f)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(versionInfo)

        val notesTitle = TextView(this).apply {
            text = "Novidades:"
            setTextColor(Color.parseColor("#202124"))
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(notesTitle)

        val notesView = TextView(this).apply {
            text = markdownToSpanned(result.releaseNotes.ifBlank { "Melhorias e correções." })
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 13.5f
            setLineSpacing(dp(2).toFloat(), 1.18f)
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(12))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                maxScrollH
            )
        }
        scroll.addView(
            notesView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(scroll)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nova Versão Disponível: v${result.latestVersion}")
            .setView(root)
            .setPositiveButton("Baixar e instalar") { _, _ -> startInAppUpdate(result) }
            .setNeutralButton("GitHub") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl)))
                } catch (_: Exception) {
                }
            }
            .setNegativeButton("Depois", null)
            .create()

        dialog.show()

        // Largura responsiva: ~92% da tela, no máximo ~420 dp (tablets).
        dialog.window?.let { w ->
            val screenW = resources.displayMetrics.widthPixels
            val width = (screenW * 0.92f).toInt().coerceAtMost(dp(420))
            w.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * Converte Markdown simples (##, **, `code`, listas -, *, 1.) em Spanned
     * sem bibliotecas externas — adequado a dispositivos com pouca RAM.
     */
    private fun markdownToSpanned(md: String): CharSequence {
        val sb = SpannableStringBuilder()
        val lines = md.replace("\r\n", "\n").split('\n')
        var first = true
        var prevBlank = false

        for (raw in lines) {
            val trimmedEnd = raw.trimEnd()
            val isBlank = trimmedEnd.isBlank()

            // Colapsa linhas em branco consecutivas em uma só.
            if (isBlank) {
                if (!first && !prevBlank) {
                    sb.append('\n')
                    prevBlank = true
                }
                continue
            }
            prevBlank = false

            if (!first) sb.append('\n')
            first = false

            var line = trimmedEnd

            // Cabeçalhos: # ## ###
            val headerMatch = Regex("^(#{1,3})\\s+(.*)").find(line)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val content = headerMatch.groupValues[2].trim()
                val startIdx = sb.length
                sb.append(content)
                val endIdx = sb.length
                sb.setSpan(StyleSpan(Typeface.BOLD), startIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val scale = when (level) {
                    1 -> 1.22f
                    2 -> 1.12f
                    else -> 1.06f
                }
                sb.setSpan(RelativeSizeSpan(scale), startIdx, endIdx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                continue
            }

            // Listas: - * + ou 1. 2.
            val listMatch = Regex("^([-*+]|\\d+\\.)\\s+(.*)").find(line.trimStart())
            if (listMatch != null) {
                line = "• " + listMatch.groupValues[2]
            }

            appendInlineMarkdown(sb, line)
        }
        return sb
    }

    private fun appendInlineMarkdown(sb: SpannableStringBuilder, line: String) {
        var i = 0
        while (i < line.length) {
            when {
                line.startsWith("**", i) -> {
                    val close = line.indexOf("**", i + 2)
                    if (close > i) {
                        val startIdx = sb.length
                        sb.append(line.substring(i + 2, close))
                        sb.setSpan(StyleSpan(Typeface.BOLD), startIdx, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = close + 2
                    } else {
                        sb.append(line[i])
                        i++
                    }
                }
                line[i] == '`' -> {
                    val close = line.indexOf('`', i + 1)
                    if (close > i) {
                        val startIdx = sb.length
                        sb.append(line.substring(i + 1, close))
                        sb.setSpan(TypefaceSpan("monospace"), startIdx, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(
                            ForegroundColorSpan(Color.parseColor("#1967D2")),
                            startIdx,
                            sb.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = close + 1
                    } else {
                        sb.append('`')
                        i++
                    }
                }
                else -> {
                    sb.append(line[i])
                    i++
                }
            }
        }
    }

    private fun showSpeechToTextDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val info = TextView(this).apply {
            text = "Cole sua chave da API Groq (gsk_…). Ela fica salva só neste aparelho e é usada para transcrever o áudio do microfone do teclado (Whisper)."
            setTextColor(Color.parseColor("#5F6368"))
            textSize = 13f
            setPadding(0, 0, 0, dp(12))
        }
        container.addView(info)

        val input = EditText(this).apply {
            hint = "gsk_..."
            setHintTextColor(Color.parseColor("#9AA0A6"))
            setTextColor(Color.parseColor("#202124"))
            setBackgroundColor(Color.parseColor("#F1F3F4"))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setText(prefs.groqApiKey)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        container.addView(input)

        val micBtn = Button(this).apply {
            text = "Conceder permissão de microfone"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1A73E8"))
            onTap { requestMicPermission() }
        }
        val micPad = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        }
        container.addView(micBtn, micPad)

        AlertDialog.Builder(this)
            .setTitle("Fala → texto")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.groqApiKey = input.text?.toString().orEmpty()
                Toast.makeText(this, if (prefs.groqApiKey.isNotBlank()) "Chave salva" else "Chave removida", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestShotsPermission() {
        if (ScreenshotSource.hasPermission(this)) {
            Toast.makeText(this, "Acesso às capturas já permitido", Toast.LENGTH_SHORT).show()
            return
        }
        requestPermissions(arrayOf(ScreenshotSource.permissionName()), REQUEST_SHOTS)
    }

    private fun requestMicPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microfone já permitido", Toast.LENGTH_SHORT).show()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SHOTS) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (ok) "Capturas de tela permitidas" else "Permissão negada: a pílula de captura não aparecerá",
                Toast.LENGTH_LONG
            ).show()
        }
        if (requestCode == REQUEST_MIC) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (ok) "Microfone permitido" else "Permissão de microfone negada",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}
