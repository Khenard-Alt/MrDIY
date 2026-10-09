package com.mrdiy.careers.ui.savedjobs

import androidx.core.view.isVisible
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.databinding.ItemSavedJobCardBinding
import com.mrdiy.careers.model.Job

class SavedJobAdapter(
    private val jobs: List<Job>,
    private val onJobClick: (Job) -> Unit,
    private val onRemoveClick: ((Job) -> Unit)? = null
) : RecyclerView.Adapter<SavedJobAdapter.SavedJobViewHolder>() {

    private var jobsList = jobs.toMutableList()

    fun updateList(newJobs: List<Job>) {
        jobsList = newJobs.toMutableList()
        notifyDataSetChanged()
    }

    inner class SavedJobViewHolder(private val binding: ItemSavedJobCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(job: Job) {
            binding.tvJobTitle.text = job.title
            binding.tvCompanyBranch.text = "${job.company} · ${job.branch}"
            val monthlyMin = job.getMonthlySalary()
            val monthlyMax = job.getMonthlySalaryMax()
            binding.tvSalary.text = if (monthlyMin == monthlyMax) {
                "₱${"%,d".format(monthlyMin)}/mo"
            } else {
                "₱${"%,d".format(monthlyMin)} – ₱${"%,d".format(monthlyMax)}/mo"
            }
            binding.chipJobType.visibility = if (job.jobType.isNotBlank() && job.jobType != "Not specified") View.VISIBLE else View.GONE
            binding.chipJobType.text = job.jobType
            binding.chipLocation.text = job.location
            binding.chipCategory.visibility = if (job.category.isNotBlank() && job.category != "Not specified") View.VISIBLE else View.GONE
            binding.chipCategory.text = job.category

            binding.jobCard.setOnClickListener { onJobClick(job) }

            binding.btnRemove.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos < jobsList.size) {
                    onRemoveClick?.invoke(job)
                    jobsList.removeAt(pos)
                    notifyItemRemoved(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedJobViewHolder {
        val binding = ItemSavedJobCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SavedJobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SavedJobViewHolder, position: Int) {
        holder.bind(jobsList[position])
    }

    override fun getItemCount() = jobsList.size
}
