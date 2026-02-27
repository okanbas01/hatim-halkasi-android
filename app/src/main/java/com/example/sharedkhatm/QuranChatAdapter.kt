package com.example.sharedkhatm

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class QuranChatAdapter(
    private val items: MutableList<QuranChatMessage>
) : RecyclerView.Adapter<QuranChatAdapter.VH>() {

    data class QuranChatMessage(
        val role: Role,
        val text: String
    ) {
        enum class Role { USER, AI }
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleAi: MaterialCardView = itemView.findViewById(R.id.bubbleAi)
        val bubbleUser: MaterialCardView = itemView.findViewById(R.id.bubbleUser)

        val txtAiHeader: TextView = itemView.findViewById(R.id.txtAiHeader)
        val cardAyet: MaterialCardView = itemView.findViewById(R.id.cardAyet)
        val txtAyetRef: TextView = itemView.findViewById(R.id.txtAyetRef)
        val txtAyetMeal: TextView = itemView.findViewById(R.id.txtAyetMeal)
        val txtAiBody: TextView = itemView.findViewById(R.id.txtAiBody)

        val layoutTyping: View = itemView.findViewById(R.id.layoutTyping)
        val dot1: View = itemView.findViewById(R.id.dot1)
        val dot2: View = itemView.findViewById(R.id.dot2)
        val dot3: View = itemView.findViewById(R.id.dot3)

        val txtUser: TextView = itemView.findViewById(R.id.txtUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quran_chat_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = items[position]

        // USER
        if (msg.role == QuranChatMessage.Role.USER) {
            holder.bubbleUser.visibility = View.VISIBLE
            holder.bubbleAi.visibility = View.GONE
            holder.txtUser.text = msg.text
            clearTyping(holder)
            return
        }

        // AI
        holder.bubbleAi.visibility = View.VISIBLE
        holder.bubbleUser.visibility = View.GONE

        // Header sabit
        holder.txtAiHeader.text = "✨ Gönül Rehberi"

        // Typing
        val isTyping = msg.text.trim() == "Cevap hazırlanıyor…"
        if (isTyping) {
            holder.cardAyet.visibility = View.GONE
            holder.txtAiBody.visibility = View.GONE
            holder.layoutTyping.visibility = View.VISIBLE
            startTyping(holder)
            return
        } else {
            clearTyping(holder)
            holder.layoutTyping.visibility = View.GONE
            holder.txtAiBody.visibility = View.VISIBLE
        }

        // ✅ 1) İstemediğin “Açıklama:” gibi etiketleri temizle
        // ✅ 2) Selam sadece ilk AI mesajda kalsın (diğer AI mesajlarda selam satırlarını kırp)
        val isFirstAiMessage = isFirstAi(position)
        val cleanedText = cleanAiText(msg.text, keepGreeting = isFirstAiMessage)

        // ✅ Ayet + body ayrıştır (ARTIK "İlgili ayet" yazmasına bağlı değil)
        val parsed = parseAyetFlexible(cleanedText)

        if (parsed != null) {
            holder.cardAyet.visibility = View.VISIBLE
            holder.txtAyetRef.text = parsed.ref   // "(İsra Suresi, 17:44)"
            holder.txtAyetMeal.text = parsed.meal // “Meal...”
            holder.txtAiBody.text = parsed.body
        } else {
            holder.cardAyet.visibility = View.GONE
            holder.txtAyetRef.text = ""
            holder.txtAyetMeal.text = ""
            holder.txtAiBody.text = cleanedText
        }
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(message: QuranChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    // ----------------------------
    // Typing helpers
    // ----------------------------
    private fun startTyping(holder: VH) {
        val anim1 = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.typing_dot)
        holder.dot1.startAnimation(anim1)

        val anim2 = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.typing_dot).apply {
            startOffset = 120
        }
        holder.dot2.startAnimation(anim2)

        val anim3 = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.typing_dot).apply {
            startOffset = 240
        }
        holder.dot3.startAnimation(anim3)
    }

    private fun clearTyping(holder: VH) {
        holder.dot1.clearAnimation()
        holder.dot2.clearAnimation()
        holder.dot3.clearAnimation()
    }

    // ----------------------------
    // Text cleanup
    // ----------------------------
    private fun isFirstAi(position: Int): Boolean {
        // İlk AI mesajını bul: genelde position 0 oluyor ama garanti olsun
        for (i in 0..position) {
            if (items[i].role == QuranChatMessage.Role.AI) {
                return i == position
            }
        }
        return position == 0
    }

    private fun cleanAiText(raw: String, keepGreeting: Boolean): String {
        var t = raw.trim()

        // Markdown kalınlıklarını temizle (**Açıklama:** gibi)
        t = t.replace("**", "")

        // "Açıklama:" etiketini kaldır (ister "Açıklama", ister "Aciklama")
        t = t.replace(Regex("""(?im)^\s*açıklama\s*:\s*"""), "")
        t = t.replace(Regex("""(?im)^\s*aciklama\s*:\s*"""), "")
        t = t.replace(Regex("""(?im)\n\s*açıklama\s*:\s*"""), "\n")
        t = t.replace(Regex("""(?im)\n\s*aciklama\s*:\s*"""), "\n")

        // "İlgili ayet:" gibi etiketleri kaldır (kart başlığımız zaten var)
        t = t.replace(Regex("""(?im)^\s*ilgili\s*ayet\s*:\s*"""), "")
        t = t.replace(Regex("""(?im)\n\s*ilgili\s*ayet\s*:\s*"""), "\n")

        if (!keepGreeting) {
            // İlk AI mesajı dışında selam satırlarını kırp
            // (Esselamü Aleyküm, Ve aleyküm selam, vb.)
            t = t.replace(
                Regex("""(?im)^\s*(esselam[üu]\s*aleyk[üu]m.*|ve\s*aleyk[üu]m\s*selam.*)\s*\n+"""),
                ""
            )
        }

        // Fazla boş satırları toparla
        t = t.replace(Regex("""\n{3,}"""), "\n\n").trim()
        return t
    }

    // ----------------------------
    // Ayet parse (flexible)
    // ----------------------------
    private data class AyetParsed(val ref: String, val meal: String, val body: String)

    /**
     * Model bazen "İlgili ayet:" yazar bazen direkt:
     * (İsra Suresi, 17:44)
     * "...."
     * açıklama...
     *
     * Bu yüzden:
     * - İlk parantez içini ref olarak al
     * - İlk tırnak içini meal olarak al
     * - Kalan metni body yap
     */
    private fun parseAyetFlexible(text: String): AyetParsed? {
        val t = text.trim()
        if (t.isBlank()) return null

        val refMatch = Regex("""\(([^)]+)\)""").find(t)
        val refRaw = refMatch?.value?.trim().orEmpty() // parantez dahil

        // Meal: ilk çift tırnak içi (") veya akıllı tırnak (“ ”) yakala
        val mealMatch =
            Regex(""""([^"]{3,})"""").find(t)
                ?: Regex("""“([^”]{3,})”""").find(t)

        val mealRaw = mealMatch?.groups?.get(1)?.value?.trim().orEmpty()

        if (refRaw.isBlank() || mealRaw.isBlank()) return null

        var body = t
        body = body.replaceFirst(refRaw, "").trim()
        // meal’i hem "..." hem “...” ihtimaline göre temizle
        body = body.replaceFirst("\"$mealRaw\"", "").trim()
        body = body.replaceFirst("“$mealRaw”", "").trim()

        // Eğer body hala boşsa bile null dönmeyelim; en azından ayet kartı görünsün
        body = body.replace(Regex("""\n{3,}"""), "\n\n").trim()

        return AyetParsed(
            ref = refRaw,
            meal = "“$mealRaw”",
            body = body
        )
    }
}
