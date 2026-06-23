package com.example.smartblindstick

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

data class Faq(val question: String, val answer: String, var isExpanded: Boolean = false)

class FaqAdapter(private val faqList: List<Faq>) : RecyclerView.Adapter<FaqAdapter.FaqViewHolder>() {

    class FaqViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.faqCard)
        val questionText: TextView = itemView.findViewById(R.id.faqQuestion)
        val answerText: TextView = itemView.findViewById(R.id.faqAnswer)
        val expandIcon: ImageView = itemView.findViewById(R.id.faqExpandIcon)
        val answerContainer: LinearLayout = itemView.findViewById(R.id.faqAnswerContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaqViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_faq, parent, false)
        return FaqViewHolder(view)
    }

    override fun onBindViewHolder(holder: FaqViewHolder, position: Int) {
        val faq = faqList[position]

        // 🔥 THE "NO DOTS" PROGRAMMATIC FIX 🔥
        // This forces the TextView to allow infinite lines, overriding any hidden theme defaults
        holder.answerText.maxLines = Int.MAX_VALUE
        holder.answerText.ellipsize = null
        holder.answerText.isSingleLine = false

        // Temporarily set the English text so the UI doesn't look blank
        holder.questionText.text = faq.question
        holder.answerText.text = faq.answer

        // Translate Question
        TranslationManager.translateText(faq.question) { translatedQuestion ->
            holder.questionText.text = translatedQuestion
        }

        // Translate Answer
        TranslationManager.translateText(faq.answer) { translatedAnswer ->
            holder.answerText.text = translatedAnswer
        }

        // Handle Expand/Collapse state
        val isExpanded = faq.isExpanded
        holder.answerContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

        // Rotate icon
        holder.expandIcon.rotation = if (isExpanded) 180f else 0f

        holder.cardView.setOnClickListener {
            faq.isExpanded = !faq.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = faqList.size
}