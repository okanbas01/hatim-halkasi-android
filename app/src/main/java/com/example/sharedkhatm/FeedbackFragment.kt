package com.example.sharedkhatm

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sharedkhatm.databinding.FragmentFeedbackBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Tavsiye / Şikayet / Destek — Geri bildirim.
 * Firestore: feedbacks (message, createdAt serverTimestamp, userId).
 * 24 saatte 1 gönderim; giriş yapmamışsa buton pasif.
 */
class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    /** Son 24 saat içinde bu kullanıcı geri bildirim gönderdiyse false. */
    private var canSendIn24h: Boolean = true

    companion object {
        private const val MIN_LENGTH = 10
        private const val MAX_LENGTH = 200
        private const val COLLECTION_FEEDBACKS = "feedbacks"
        private const val TAG = "FeedbackFragment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedbackBinding.bind(view)

        setupBackButton()
        setupTextWatcher()
        setupSendButton()
        checkAuthAnd24hLimit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupBackButton() {
        binding.btnFeedbackBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupTextWatcher() {
        binding.etFeedback.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                binding.tvFeedbackCounter.text = "$len/$MAX_LENGTH"
                binding.tilFeedback.isErrorEnabled = false
                updateSendButtonState()
            }
        })
    }

    private fun setupSendButton() {
        binding.btnFeedbackSend.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(requireContext(), "Geri bildirim gönderebilmek için giriş yapmalısınız.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!canSendIn24h) {
                Toast.makeText(requireContext(), "24 saat içinde tekrar geri bildirim gönderebilirsiniz.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val text = binding.etFeedback.text?.toString()?.trim().orEmpty()
            if (!isFeedbackValid(text)) {
                binding.tilFeedback.error = "En az 10 karakter yazmalısınız"
                binding.tilFeedback.isErrorEnabled = true
                return@setOnClickListener
            }
            sendFeedback(text)
        }
    }

    /** Sadece sayfa açıldığında bir kez çalışır; listener yok. */
    private fun checkAuthAnd24hLimit() {
        binding.tvFeedbackStatus.visibility = View.GONE
        val user = auth.currentUser
        if (user == null) {
            canSendIn24h = false
            binding.btnFeedbackSend.isEnabled = false
            binding.btnFeedbackSend.alpha = 0.5f
            binding.tvFeedbackStatus.text = "Geri bildirim gönderebilmek için giriş yapmalısınız."
            binding.tvFeedbackStatus.visibility = View.VISIBLE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val hasRecent = withContext(Dispatchers.IO) {
                hasFeedbackInLast24Hours(user.uid)
            }
            canSendIn24h = !hasRecent
            withContext(Dispatchers.Main) {
                if (hasRecent) {
                    binding.btnFeedbackSend.alpha = 0.5f
                    binding.btnFeedbackSend.isEnabled = true
                    binding.tvFeedbackStatus.text = "Yeni geri bildirim göndermek için 24 saat beklemelisiniz."
                    binding.tvFeedbackStatus.visibility = View.VISIBLE
                } else {
                    binding.btnFeedbackSend.alpha = 1f
                    binding.tvFeedbackStatus.visibility = View.GONE
                    updateSendButtonState()
                }
            }
        }
    }

    /** Firestore: feedbacks içinde userId == uid ve createdAt >= (now - 24h) kayıt var mı? Tek seferlik get(). */
    private suspend fun hasFeedbackInLast24Hours(uid: String): Boolean {
        return try {
            val millis = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
            val timestamp24hAgo = Timestamp(Date(millis))
            val snapshot = db.collection(COLLECTION_FEEDBACKS)
                .whereEqualTo("userId", uid)
                .whereGreaterThanOrEqualTo("createdAt", timestamp24hAgo)
                .limit(1)
                .get()
                .await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            Log.e(TAG, "24h check failed", e)
            false
        }
    }

    private fun updateSendButtonState() {
        if (!canSendIn24h) return
        if (auth.currentUser == null) return
        val text = binding.etFeedback.text?.toString()?.trim().orEmpty()
        binding.btnFeedbackSend.isEnabled = isFeedbackValid(text)
    }

    private fun isFeedbackValid(text: String): Boolean {
        val t = text.trim()
        return t.length in MIN_LENGTH..MAX_LENGTH
    }

    private fun sendFeedback(text: String) {
        val uid = auth.currentUser?.uid ?: return
        binding.btnFeedbackSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.collection(COLLECTION_FEEDBACKS)
                        .add(mapOf(
                            "message" to text,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "userId" to uid
                        ))
                        .await()
                }
                withContext(Dispatchers.Main) {
                    showSuccessDialog()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Feedback send failed", e)
                withContext(Dispatchers.Main) {
                    binding.btnFeedbackSend.isEnabled = true
                    showErrorDialog()
                }
            }
        }
    }

    private fun showSuccessDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_feedback_success, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnDialogOk).setOnClickListener {
            dialog.dismiss()
            binding.etFeedback.text?.clear()
            binding.tvFeedbackCounter.text = "0/$MAX_LENGTH"
            binding.btnFeedbackSend.isEnabled = false
            canSendIn24h = false
            binding.btnFeedbackSend.alpha = 0.5f
            binding.tvFeedbackStatus.text = "Yeni geri bildirim göndermek için 24 saat beklemelisiniz."
            binding.tvFeedbackStatus.visibility = View.VISIBLE
        }
        dialog.show()
        binding.btnFeedbackSend.isEnabled = true
    }

    private fun showErrorDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage("Bir hata oluştu, lütfen tekrar deneyin.")
            .setPositiveButton("Tamam", null)
            .setCancelable(true)
            .show()
    }
}
