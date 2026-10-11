package com.mrdiy.careers.ui.home

import androidx.core.view.isVisible
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.ItemJobCardBinding
import com.mrdiy.careers.model.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job as CoroutineJob

class JobAdapter(
    private var jobs: List<Job>,
    private val onJobClick: (Job) -> Unit,
    private val onSaveClick: ((Job) -> Unit)? = null
) : RecyclerView.Adapter<JobAdapter.JobViewHolder>() {

    private var savedJobIds: Set<String> = emptySet()
    private var profile: com.mrdiy.careers.model.UserProfile? = null
    private var scores: Map<String, Int> = emptyMap()
    private var distances: Map<String, Double> = emptyMap()

    /** Distance in km from the applicant to each job (by id); shown next to the location. */
    fun setDistances(value: Map<String, Double>) {
        if (value == distances) return
        distances = value
        notifyDataSetChanged()
    }

    // Match scoring does an O(n^2)-ish TF-IDF pass over the whole job list,
    // so it runs off the main thread; the last-dispatched job is cancelled
    // whenever a newer profile/job-list update supersedes it. The scope is
    // recreated on each attach since a RecyclerView (e.g. inside a
    // ViewPager2 page) can be detached and reattached, and a cancelled
    // CoroutineScope never resumes launching work.
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scoringJob: CoroutineJob? = null

    fun updateProfile(value: com.mrdiy.careers.model.UserProfile?) {
        profile = value
        recomputeScoresAsync()
    }

    private fun recomputeScoresAsync() {
        scoringJob?.cancel()

        val profileSnapshot = profile
        val jobsSnapshot = jobs

        val matchable = profileSnapshot?.takeIf { it.canShowMatch }

        if (matchable == null) {
            scores = emptyMap()
            notifyDataSetChanged()
            return
        }

        scoringJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                com.mrdiy.careers.data.ml.JobMatchingEngine.matchJobs(matchable, jobsSnapshot)
                    .associate { it.job.id to it.matchScore }
            }
            scores = result
            notifyDataSetChanged()
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope.cancel()
    }

    // ───────── Update Jobs ─────────

    fun updateList(newJobs: List<Job>) {

        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {

                override fun getOldListSize() =
                    jobs.size

                override fun getNewListSize() =
                    newJobs.size

                override fun areItemsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {

                    return jobs[
                        oldItemPosition
                    ].id ==
                            newJobs[
                                newItemPosition
                            ].id
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {

                    return jobs[
                        oldItemPosition
                    ] ==
                            newJobs[
                                newItemPosition
                            ]
                }
            }
        )

        jobs = newJobs
        recomputeScoresAsync()

        diff.dispatchUpdatesTo(this)
    }

    // ───────── Saved Jobs ─────────

    fun updateSavedJobs(
        newSavedIds: Set<String>
    ) {

        val changedIds =
            savedJobIds
                .symmetricDifference(
                    newSavedIds
                )

        savedJobIds = newSavedIds

        jobs.forEachIndexed { index, job ->

            if (
                job.id in changedIds
            ) {

                notifyItemChanged(
                    index,
                    PAYLOAD_SAVE_STATE
                )
            }
        }
    }

    // ───────── ViewHolder ─────────

    inner class JobViewHolder(
        private val binding:
        ItemJobCardBinding
    ) : RecyclerView.ViewHolder(
        binding.root
    ) {

        fun bind(job: Job) {
            binding.tvMatchScore.text = scores[job.id]?.let { "$it%" } ?: "—"
            binding.tvMatchScore.contentDescription = scores[job.id]?.let { "$it percent profile match" } ?: "Complete your profile for a match score"

            binding.tvJobTitle.text =
                job.title

            binding.tvCompanyBranch.text =
                "${job.company} · ${job.branch}"
            binding.tvJobReference.text = "REF #${job.id}"

            binding.tvPostedTime.text =
                job.postedAgo

            binding.chipJobType.visibility = if (job.jobType.isNotBlank() && job.jobType != "Not specified") View.VISIBLE else View.GONE
            binding.chipCategory.visibility = if (job.category.isNotBlank() && job.category != "Not specified") View.VISIBLE else View.GONE
            binding.chipJobType.text =
                job.jobType

            binding.chipLocation.text = distances[job.id]
                ?.let { "${com.mrdiy.careers.data.search.LocationDistance.formatDistance(it)} · ${job.location}" }
                ?: job.location

            binding.chipCategory.text =
                job.category

            // Salary display
            val monthlyMin = job.getMonthlySalary()
            val monthlyMax = job.getMonthlySalaryMax()

            binding.tvSalary.text =
                when {
                    job.salaryMin == 0 && job.salaryMax == 0 ->
                        "Salary not disclosed"

                    monthlyMin == monthlyMax ->
                        "₱${"%,d".format(monthlyMin)}/mo"

                    else ->
                        "₱${"%,d".format(monthlyMin)} – ₱${"%,d".format(monthlyMax)}/mo"
                }

            bindSaveButton(job)

            binding.jobCard
                .setOnClickListener {

                    onJobClick(job)
                }
        }

        fun bindSaveButton(
            job: Job
        ) {

            val isSaved =
                savedJobIds.contains(
                    job.id
                )

            binding.btnSaveJob
                .setImageResource(

                    if (isSaved)

                        R.drawable
                            .ic_bookmark_filled

                    else

                        R.drawable
                            .ic_bookmark_outline
                )

            binding.btnSaveJob
                .setColorFilter(

                    ContextCompat
                        .getColor(

                            binding.root.context,

                            if (isSaved)

                                R.color
                                    .orange_primary

                            else

                                R.color
                                    .text_muted
                        )
                )

            binding.btnSaveJob
                .setOnClickListener {

                    onSaveClick
                        ?.invoke(job)
                }
        }
    }

    // ───────── Adapter Overrides ─────────

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): JobViewHolder {

        val binding =
            ItemJobCardBinding
                .inflate(

                    LayoutInflater
                        .from(
                            parent.context
                        ),

                    parent,

                    false
                )

        return JobViewHolder(
            binding
        )
    }

    override fun onBindViewHolder(
        holder: JobViewHolder,
        position: Int
    ) {

        holder.bind(
            jobs[position]
        )
    }

    override fun onBindViewHolder(
        holder: JobViewHolder,
        position: Int,
        payloads: List<Any>
    ) {

        if (
            payloads.contains(
                PAYLOAD_SAVE_STATE
            )
        ) {

            holder.bindSaveButton(
                jobs[position]
            )

        } else {

            super.onBindViewHolder(
                holder,
                position,
                payloads
            )
        }
    }

    override fun getItemCount() =
        jobs.size

    // ───────── Helpers ─────────

    private fun <T> Set<T>
            .symmetricDifference(
        other: Set<T>
    ): Set<T> {

        return (this - other) +
                (other - this)
    }

    companion object {

        private const val
                PAYLOAD_SAVE_STATE =
            "save_state"
    }
}
