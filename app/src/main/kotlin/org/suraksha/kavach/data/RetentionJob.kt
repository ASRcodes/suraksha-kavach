package org.suraksha.kavach.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.suraksha.kavach.KavachApplication
import java.util.concurrent.TimeUnit

class RetentionJob : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as KavachApplication
        work = app.scope.launch {
            app.pruneHistory()
            jobFinished(params, false)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }

    companion object {
        fun schedule(context: Context) {
            val info = JobInfo.Builder(810, ComponentName(context, RetentionJob::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(12))
                .setPersisted(true).build()
            context.getSystemService(JobScheduler::class.java).schedule(info)
        }
    }
}
