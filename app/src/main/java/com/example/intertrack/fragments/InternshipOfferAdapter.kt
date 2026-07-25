package com.example.intertrack.fragments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.intertrack.data.model.FirestoreInternshipOffer
import com.example.intertrack.databinding.ItemInternshipOfferBinding

class InternshipOfferAdapter(
    private val items: List<FirestoreInternshipOffer>,
    private val onItemClick: (FirestoreInternshipOffer) -> Unit
) : RecyclerView.Adapter<InternshipOfferAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemInternshipOfferBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(offer: FirestoreInternshipOffer) {
            val initials = offer.companyName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifBlank { "CO" }

            binding.tvOfferItemInitials.text = initials
            binding.tvOfferItemCompany.text = offer.companyName.ifBlank { "Company" }
            binding.tvOfferItemTitle.text = offer.title.ifBlank { "Internship Position" }
            binding.tvOfferItemDepartment.text = offer.department.ifBlank { "General" }
            binding.tvOfferItemDuration.text =
                if (offer.duration.isNotBlank()) "${offer.duration} wks" else "Open"

            if (offer.status == "OPEN") {
                binding.tvOfferItemStatus.text = "Open"
                binding.tvOfferItemStatus.setTextColor(android.graphics.Color.parseColor("#16A34A"))
            } else {
                binding.tvOfferItemStatus.text = "Closed"
                binding.tvOfferItemStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            }

            binding.root.setOnClickListener { onItemClick(offer) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInternshipOfferBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
