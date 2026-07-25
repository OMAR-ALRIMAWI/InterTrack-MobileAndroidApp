package com.example.intertrack.fragments

import android.app.DatePickerDialog
import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.intertrack.R
import com.example.intertrack.data.model.WeekUtil
import java.util.Calendar

/**
 * Small reusable dialog to pick the internship Start/End dates. Validates (both required, end after
 * start, at least 1 week) and returns the millis + computed weekly report count. Reused by the
 * instructor connect flow and the company "Set Internship Period" flow.
 */
object InternshipPeriodDialog {

    fun show(
        context: Context,
        initialStartMs: Long = 0L,
        initialEndMs: Long = 0L,
        onConfirm: (startMs: Long, endMs: Long, requiredReports: Int) -> Unit
    ) {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_internship_period, null)
        val tvStart = view.findViewById<TextView>(R.id.tvPeriodStart)
        val tvEnd = view.findViewById<TextView>(R.id.tvPeriodEnd)
        val tvSummary = view.findViewById<TextView>(R.id.tvPeriodSummary)

        var startMs = initialStartMs.takeIf { it > 0L } ?: 0L
        var endMs = initialEndMs.takeIf { it > 0L } ?: 0L

        fun refresh() {
            tvStart.text = if (startMs > 0L) WeekUtil.formatDate(startMs) else "Select start date"
            tvEnd.text = if (endMs > 0L) WeekUtil.formatDate(endMs) else "Select end date"
            if (startMs > 0L && endMs > 0L && endMs >= startMs) {
                tvSummary.text = "Weekly reports required: ${WeekUtil.requiredReports(startMs, endMs)}"
            } else {
                tvSummary.text = ""
            }
        }
        refresh()

        fun pick(currentMs: Long, onPicked: (Long) -> Unit) {
            val cal = Calendar.getInstance()
            if (currentMs > 0L) cal.timeInMillis = currentMs
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val c = Calendar.getInstance()
                    c.set(y, m, d, 0, 0, 0); c.set(Calendar.MILLISECOND, 0)
                    onPicked(c.timeInMillis)
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        tvStart.setOnClickListener { pick(startMs) { startMs = it; refresh() } }
        tvEnd.setOnClickListener { pick(endMs) { endMs = it; refresh() } }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Internship Period")
            .setView(view)
            .setPositiveButton("Confirm", null)   // set below so we can validate before dismiss
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                when {
                    startMs <= 0L -> Toast.makeText(context, "Please select a start date.", Toast.LENGTH_SHORT).show()
                    endMs <= 0L -> Toast.makeText(context, "Please select an end date.", Toast.LENGTH_SHORT).show()
                    endMs <= startMs -> Toast.makeText(context, "End date must be after the start date.", Toast.LENGTH_SHORT).show()
                    (endMs - startMs) < 6 * WeekUtil.DAY_MS ->
                        Toast.makeText(context, "Internship must be at least 1 week.", Toast.LENGTH_SHORT).show()
                    else -> {
                        onConfirm(startMs, endMs, WeekUtil.requiredReports(startMs, endMs))
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }
}
