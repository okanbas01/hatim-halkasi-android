// QuranChatFragment.kt
package com.example.sharedkhatm

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

class QuranChatFragment : Fragment(R.layout.fragment_quran_chat) {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rv: RecyclerView
    private lateinit var et: TextInputEditText
    private lateinit var btnSend: FloatingActionButton

    private val messages = mutableListOf<QuranChatAdapter.QuranChatMessage>()
    private val adapter = QuranChatAdapter(messages)

    // ✅ FeatureGate
    private var gateReg: ListenerRegistration? = null
    @Volatile private var gonulEnabled: Boolean = true
    @Volatile private var gonulDisabledMsg: String = "Bu özellik şu an bakımda."

    private val systemPrompt: String =
        "Sen İslami konularda hassas, nazik ve ölçülü bir rehbersin. " +
                "Kullanıcının mesajına uygun şekilde Kur'an-ı Kerim'den ilgili ayeti seç. " +
                "Ayet için: (Sure Adı, Ayet No) + kısa bir meal yaz. " +
                "Ardından 2-4 cümleyle sade, şefkatli bir açıklama ekle. " +
                "Asla dini hüküm/fetva verme. " +
                "Kesin yargı, korkutma, suçlama dili kullanma. " +
                "Kullanıcı kendine zarar/kriz belirtisi verirse profesyonel destek önermeyi unutma. " +
                "KESİN KURALLAR: Selamlama yazma (Esselamü Aleyküm vb. yok). " +
                "Markdown/başlık kullanma (**, *, Açıklama:, Cevap: vb. yok). " +
                "EK KURAL: Kullanıcı küfür, cinsel içerik, inançlara hakaret, nefret söylemi veya güncel siyaset yazarsa ayet verme. " +
                "Sadece kısa bir şekilde saygılı sınır koy ve kullanıcıyı daha uygun bir dille yeniden yazmaya davet et. " +
                "Bu durumlarda parantezli ayet referansı ve tırnaklı meal yazma."

    private val maxHistoryForPrompt = 10

    private enum class UserMsgClass {
        NORMAL,
        GIBBERISH,
        NUMERIC_SPAM,
        SEXUAL,
        PEDO,
        INCEST,
        ZOO,
        PROFANITY,
        RELIGION_INSULT,
        HATE,
        VIOLENCE,
        POLITICAL
    }

    private data class GuardResult(
        val clazz: UserMsgClass,
        val reply: String? = null
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbarChat)
        rv = view.findViewById(R.id.rvChat)
        et = view.findViewById(R.id.etMessage)
        btnSend = view.findViewById(R.id.btnSend)

        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }

        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // ✅ Kill switch dinle: kapalıysa ekrana girer girmez geri at
        bindGonulFeatureGate()

        // ✅ Kapalıysa hiç içerik göstermeden çık
        val stateNow = FeatureGate.getState()
        gonulEnabled = stateNow.enabled
        gonulDisabledMsg = stateNow.message
        if (!gonulEnabled) {
            Toast.makeText(requireContext(), gonulDisabledMsg, Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
            return
        }

        // ✅ İlk mesaj
        if (messages.isEmpty()) {
            adapter.addMessage(
                QuranChatAdapter.QuranChatMessage(
                    role = QuranChatAdapter.QuranChatMessage.Role.AI,
                    text = "Esselamü aleyküm ve rahmetullah 🌿\n" +
                            "Burası kalbinden geçenleri paylaşabileceğin bir alan.\n" +
                            "Dilersen bir soru sor ya da içinden geleni yaz…"
                )
            )
            rv.scrollToPosition(adapter.itemCount - 1)
        }

        et.setOnEditorActionListener { _, actionId, event ->
            val isSend =
                actionId == EditorInfo.IME_ACTION_SEND ||
                        (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)

            if (isSend) {
                btnSend.performClick()
                true
            } else false
        }

        btnSend.setOnClickListener {
            val userText = et.text?.toString()?.trim().orEmpty()
            if (userText.isBlank()) return@setOnClickListener
            et.setText("")
            sendUserMessage(userText)
        }
    }

    private fun bindGonulFeatureGate() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                FeatureGate.refreshRemoteConfig()
            } catch (_: Throwable) {}

            gateReg?.remove()
            gateReg = FeatureGate.listenKillSwitch { state ->
                gonulEnabled = state.enabled
                gonulDisabledMsg = state.message

                if (!isAdded) return@listenKillSwitch
                if (!state.enabled) {
                    Toast.makeText(requireContext(), gonulDisabledMsg, Toast.LENGTH_LONG).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }


    private fun sendUserMessage(userText: String) {
        adapter.addMessage(
            QuranChatAdapter.QuranChatMessage(
                role = QuranChatAdapter.QuranChatMessage.Role.USER,
                text = userText
            )
        )
        rv.scrollToPosition(adapter.itemCount - 1)

        if (!gonulEnabled) {
            adapter.addMessage(
                QuranChatAdapter.QuranChatMessage(
                    role = QuranChatAdapter.QuranChatMessage.Role.AI,
                    text = gonulDisabledMsg
                )
            )
            rv.scrollToPosition(adapter.itemCount - 1)
            return
        }

        val guard = guardUserMessage(userText)
        if (guard.clazz != UserMsgClass.NORMAL) {
            adapter.addMessage(
                QuranChatAdapter.QuranChatMessage(
                    role = QuranChatAdapter.QuranChatMessage.Role.AI,
                    text = guard.reply ?: "Mesajını biraz daha uygun bir dille yazar mısın?"
                )
            )
            rv.scrollToPosition(adapter.itemCount - 1)
            return
        }

        adapter.addMessage(
            QuranChatAdapter.QuranChatMessage(
                role = QuranChatAdapter.QuranChatMessage.Role.AI,
                text = "Cevap hazırlanıyor…"
            )
        )
        val typingIndex = messages.lastIndex
        rv.scrollToPosition(adapter.itemCount - 1)

        setSendingState(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!gonulEnabled) {
                    messages[typingIndex] = QuranChatAdapter.QuranChatMessage(
                        role = QuranChatAdapter.QuranChatMessage.Role.AI,
                        text = gonulDisabledMsg
                    )
                    adapter.notifyItemChanged(typingIndex)
                    rv.scrollToPosition(adapter.itemCount - 1)
                    return@launch
                }

                val prompt = buildPrompt(userText)

                val primary = Firebase.ai(backend = GenerativeBackend.googleAI())
                    .generativeModel(modelName = "gemini-2.5-flash")

                val responseText = withContext(Dispatchers.IO) {
                    try {
                        primary.generateContent(prompt).text?.trim().orEmpty()
                    } catch (e: Throwable) {
                        val fallback = Firebase.ai(backend = GenerativeBackend.googleAI())
                            .generativeModel(modelName = "gemini-2.0-flash")
                        fallback.generateContent(prompt).text?.trim().orEmpty()
                    }
                }

                if (!isAdded) return@launch

                val finalText =
                    if (responseText.isNotBlank()) sanitizeAiText(responseText)
                    else "Şu an net bir cevap üretemedim. Biraz daha detay yazar mısın?"

                messages[typingIndex] = QuranChatAdapter.QuranChatMessage(
                    role = QuranChatAdapter.QuranChatMessage.Role.AI,
                    text = finalText
                )
                adapter.notifyItemChanged(typingIndex)
                rv.scrollToPosition(adapter.itemCount - 1)

            } catch (t: Throwable) {
                if (!isAdded) return@launch
                val msg = t.message ?: t.toString()

                messages[typingIndex] = QuranChatAdapter.QuranChatMessage(
                    role = QuranChatAdapter.QuranChatMessage.Role.AI,
                    text = "Şu an teknik bir sorun oluştu.\n\nDetay: $msg"
                )
                adapter.notifyItemChanged(typingIndex)
                rv.scrollToPosition(adapter.itemCount - 1)

                Toast.makeText(requireContext(), "AI hata: $msg", Toast.LENGTH_LONG).show()
            } finally {
                if (isAdded) setSendingState(false)
            }
        }
    }

    private fun setSendingState(isSending: Boolean) {
        btnSend.isEnabled = !isSending
        et.isEnabled = !isSending
        btnSend.alpha = if (isSending) 0.6f else 1f
    }

    private fun buildPrompt(latestUserMessage: String): String {
        val recent = messages
            .filterNot {
                it.role == QuranChatAdapter.QuranChatMessage.Role.AI &&
                        it.text.contains("Cevap hazırlanıyor")
            }
            .takeLast(maxHistoryForPrompt)

        val historyText = buildString {
            for (m in recent) {
                when (m.role) {
                    QuranChatAdapter.QuranChatMessage.Role.USER -> append("Kullanıcı: ${m.text}\n")
                    QuranChatAdapter.QuranChatMessage.Role.AI -> append("Rehber: ${m.text}\n")
                }
            }
        }

        return """
            $systemPrompt

            Sohbet geçmişi:
            $historyText

            Kullanıcı: $latestUserMessage

            Tek paragraf cevap ver:
            (Sure Adı, Ayet No) "kısa meal" + 2-4 cümle açıklama
        """.trimIndent()
    }

    private fun sanitizeAiText(raw: String): String {
        var s = raw.trim()
        s = s.replace("**", "").replace("*", "").replace("#", "")

        val badPrefixes = listOf("Açıklama:", "ACIKLAMA:", "AÇIKLAMA:", "Cevap:", "CEVAP:", "Not:", "NOT:")
        badPrefixes.forEach { p ->
            s = s.replace(Regex("(?im)^\\s*${Regex.escape(p)}\\s*"), "")
        }

        s = s.replace(
            Regex("(?im)^\\s*(esselamü\\s*aleyküm|esselamu\\s*aleykum|aleyküm\\s*selam|ve\\s*aleyküm\\s*selam|selam).*\\n+"),
            ""
        )

        s = s.replace(Regex("\\n{3,}"), "\n\n").trim()
        return s
    }

    override fun onDestroyView() {
        gateReg?.remove()
        gateReg = null
        super.onDestroyView()
    }

    // ----------------------------
    // Guard helpers (senin kodun)
    // ----------------------------

    private fun guardUserMessage(input: String): GuardResult {
        val original = input.trim()
        if (original.isBlank()) {
            return GuardResult(
                UserMsgClass.GIBBERISH,
                "Kalbinden geçenleri bir cümleyle biraz daha açık yazar mısın? Ben de daha iyi eşlik edeyim."
            )
        }

        val norm = normalizeForMatch(original)
        val normSpaced = normalizeKeepSpaces(original)

        val digits = original.count { it.isDigit() }
        val letters = original.count { it.isLetter() }
        val total = max(1, original.length)
        val digitRatio = digits.toDouble() / total.toDouble()

        if (digits >= 6 && letters == 0) {
            return GuardResult(UserMsgClass.NUMERIC_SPAM, "Bunu tam anlayamadım. Bir cümleyle ne danışmak istediğini yazar mısın?")
        }
        if (digitRatio > 0.70 && digits >= 8) {
            return GuardResult(UserMsgClass.NUMERIC_SPAM, "Mesajın çoğu sayılardan oluşuyor. Ne sormak istediğini kısa bir cümleyle yazar mısın?")
        }

        if (looksLikeGibberish(original)) {
            return GuardResult(
                UserMsgClass.GIBBERISH,
                "Mesajını tam anlayamadım. Biraz daha açık ve kısa yazabilir misin? Örneğin: ne oldu, ne hissediyorsun, neye ihtiyaç duyuyorsun?"
            )
        }

        val religionInsultCompact = listOf(
            "allahyok", "allahuydurma", "allahyalan", "allahsacma",
            "dinyalan", "dinsacma", "dinhikaye",
            "kuranyalan", "kuranuydurma", "kuransacma", "kuranhikaye",
            "islamyalan", "islamsacma", "islamsahte",
            "peygamberyalanci", "peygamberuydurma",
            "muhammedyalanci", "muhammeduydurma", "muhammedsahte"
        )

        val religionTargets = listOf("allah", "din", "kuran", "islam", "peygamber", "muhammed", "resul", "rasul", "nebî", "nebi")
        val religionInsultPhrases = listOf("yalan", "yalanci", "yalancı", "uydurma", "sahte", "sacma", "saçma", "hikaye", "masal", "dolandirici", "dolandırıcı")

        val hasReligionTarget = containsAnyWord(normSpaced, religionTargets)
        val hasInsultPhrase = containsAnyWord(normSpaced, religionInsultPhrases)

        if (containsAny(norm, religionInsultCompact) || (hasReligionTarget && hasInsultPhrase)) {
            return GuardResult(
                UserMsgClass.RELIGION_INSULT,
                "Bu alanda inançlara ve kutsal değerlere hakaret içeren ifadelerle devam edemem. Eğer bir şüphe veya itirazın varsa, saygılı bir dille sorarsan konuşabiliriz."
            )
        }

        val politicalWords = listOf(
            "parti","secim","seçim","oy ver","propaganda","miting",
            "cumhurbaskani","cumhurbaşkanı","bakan","milletvekili","hukumet","hükümet",
            "akp","chp","mhp","hdp","iyi parti","zafer","tip",
            "erdogan","erdoğan","imamoglu","imamoğlu"
        )
        if (containsAnyWord(normSpaced, politicalWords.map { normalizeKeepSpaces(it) })) {
            return GuardResult(
                UserMsgClass.POLITICAL,
                "Bu alanda güncel siyaset ve propaganda içeren konulara giremiyorum. İstersen daha genel bir şekilde sabır, adalet, kul hakkı ve güzel ahlak gibi başlıklarda konuşabiliriz."
            )
        }

        val violenceWords = listOf("oldur","öldür","geber","vur","kes","yak","bomba","patlat","intikam")
        if (containsAnyWord(normSpaced, violenceWords.map { normalizeKeepSpaces(it) })) {
            return GuardResult(
                UserMsgClass.VIOLENCE,
                "Zarar veya şiddet çağrışımı yapan ifadelerle devam edemem. İstersen seni bu noktaya getiren duyguyu konuşalım; daha sakin bir yerden ele alabiliriz."
            )
        }

        val sexualWords = listOf("seks","cinsel","porno","porn","mastur","masturb","vajina","penis","oral","escort","fuhus","fuhuş")
        if (containsAnyWord(normSpaced, sexualWords.map { normalizeKeepSpaces(it) }) || containsAny(norm, listOf("porn"))) {
            return GuardResult(
                UserMsgClass.SEXUAL,
                "Bu alanda cinsel içeriklere giremiyorum. İstersen mahremiyet, sınırlar, aile içi iletişim ve kalbi korumak gibi daha genel bir çerçevede konuşabiliriz."
            )
        }

        val profanityCompact = listOf("amk","aq","orospu","siktir")
        if (containsAny(norm, profanityCompact)) {
            return GuardResult(
                UserMsgClass.PROFANITY,
                "Bunu daha kırıcı olmayan bir dille yazarsan sevinirim. Ben burada sakin ve saygılı bir üslupla yardımcı olabilirim."
            )
        }

        return GuardResult(UserMsgClass.NORMAL)
    }

    private fun normalizeForMatch(s: String): String {
        val lowered = s.lowercase()
        val tr = lowered
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')

        val sb = StringBuilder(tr.length)
        for (ch in tr) if (ch.isLetterOrDigit()) sb.append(ch)
        return sb.toString()
    }

    private fun normalizeKeepSpaces(s: String): String {
        val lowered = s.lowercase()
        val tr = lowered
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ı', 'i')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')

        val sb = StringBuilder(tr.length)
        for (ch in tr) {
            when {
                ch.isLetterOrDigit() -> sb.append(ch)
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(' ')
            }
        }
        return sb.toString().replace(Regex("""\s{2,}"""), " ").trim()
    }

    private fun containsAny(haystackCompact: String, needles: List<String>): Boolean {
        val h = haystackCompact.lowercase()
        for (nRaw in needles) {
            val n = nRaw.trim().lowercase()
            if (n.isNotBlank() && h.contains(n)) return true
        }
        return false
    }

    private fun containsAnyWord(haystackSpacedNorm: String, needles: List<String>): Boolean {
        val h = haystackSpacedNorm.lowercase()
        for (nRaw in needles) {
            val n = nRaw.trim().lowercase()
            if (n.isBlank() || n.length < 3) continue
            val r = Regex("""\b${Regex.escape(n)}\b""")
            if (r.containsMatchIn(h)) return true
        }
        return false
    }

    private fun looksLikeGibberish(t: String): Boolean {
        val s = t.trim()
        if (s.length < 2) return true

        val letters = s.count { it.isLetter() }
        val total = max(1, s.length)
        val ratioLetters = letters.toDouble() / total.toDouble()
        if (ratioLetters < 0.30) return true

        if (Regex("""(.)\1{5,}""").containsMatchIn(s)) return true

        val words = s.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size >= 3) {
            val w0 = words[0].lowercase()
            val allSame = words.all { it.lowercase() == w0 }
            if (allSame) return true
        }

        if (words.size == 1 && words[0].length >= 18) return true

        val digits = s.count { it.isDigit() }
        if (s.length <= 4 && digits >= 2) return true

        return false
    }
}
