package com.example.intertrack.data.cache

import com.example.intertrack.data.model.FirestoreCompany
import com.example.intertrack.data.model.User

object AppSessionCache {
    var currentUser: User? = null
    var currentCompany: FirestoreCompany? = null

    fun clear() {
        currentUser = null
        currentCompany = null
    }
}
