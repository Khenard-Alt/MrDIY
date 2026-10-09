package com.mrdiy.careers.ui.recommendations

import android.graphics.Color
import androidx.core.view.isVisible
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.ItemRecommendedJobCardBinding
import com.mrdiy.careers.model.Job
import com.mrdiy.careers.model.JobMatchResult

class RecommendedJobAdapter(
    private var results: List<JobMatchResult>,
    private val onJobClick: (Job) -> Unit
) : RecyclerView.Adapter<RecommendedJobAdapter.ViewHolder>() {

    fun updateResults(items: List<JobMatchResult>) {
        results = items
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemRecommendedJobCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(result: JobMatchResult) {
            val job = result.job

            binding.tvJobTitle.text = job.title
            binding.tvCompanyBranch.text = "Ref. ${job.id} · ${job.company} · ${job.branch}"
            val monthlyMin = job.getMonthlySalary()
            val monthlyMax = job.getMonthlySalaryMax()
            binding.tvSalary.text = if (monthlyMin == 0 && monthlyMax == 0) {
                "Salary not disclosed"
            } else if (monthlyMin == monthlyMax) {
                "₱${"%,d".format(monthlyMin)}/mo"
            } else {
                "₱${"%,d".format(monthlyMin)} – ₱${"%,d".format(monthlyMax)}/mo"
            }
            binding.chipJobType.visibility = if (job.jobType.isNotBlank() && job.jobType != "Not specified") View.VISIBLE else View.GONE
            binding.chipJobType.text = job.jobType
            binding.chipLocation.text = job.location
            binding.chipCategory.visibility = if (job.category.isNotBlank() && job.category != "Not specified") View.VISIBLE else View.GONE
            binding.chipCategory.text = job.category

            // ── Match score badge ─────────────────────────────────────────────
            binding.tvMatchScore.text = "${result.matchScore}%"
            binding.tvMatchLabel.text = "Match"
            binding.matchBadge.contentDescription = "${result.matchScore}% match: ${result.matchLabel}"

            val (badgeColor, labelColor) = Pair(R.color.brand_yellow, R.color.brand_black)
            binding.matchBadge.setCardBackgroundColor(
                ContextCompat.getColor(binding.root.context, badgeColor)
            )
            binding.tvMatchScore.setTextColor(
                ContextCompat.getColor(binding.root.context, labelColor)
            )
            binding.tvMatchLabel.setTextColor(
                ContextCompat.getColor(binding.root.context, labelColor)
            )

            // ── Matched skills chips (reusing views to avoid GC allocations during scroll) ──
            val skillsToShow = result.matchedSkills.take(3)
            val chipGroup = binding.chipGroupMatchedSkills

            skillsToShow.forEachIndexed { index, skill ->
                val chip = if (index < chipGroup.childCount) {
                    (chipGroup.getChildAt(index) as? com.google.android.material.chip.Chip)
                } else {
                    com.google.android.material.chip.Chip(binding.root.context).apply {
                        isClickable = false
                        setChipBackgroundColorResource(R.color.chip_skill_matched_bg)
                        setTextColor(ContextCompat.getColor(context, R.color.chip_skill_matched_text))
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        chipGroup.addView(this)
                    }
                }
                chip?.apply {
                    text = skill
                    visibility = View.VISIBLE
                }
            }

            for (i in skillsToShow.size until chipGroup.childCount) {
                chipGroup.getChildAt(i).visibility = View.GONE
            }

            // ── First match reason ────────────────────────────────────────────
            binding.tvMatchReason.text = result.matchReasons.firstOrNull() ?: ""

            binding.jobCard.setOnClickListener { onJobClick(job) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemRecommendedJobCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(results[position])

    override fun getItemCount() = results.size
}
