package com.example.sharedkhatm

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FreeReadingFragment : Fragment(R.layout.fragment_free_reading) {

    private lateinit var adapter: SurahAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerSurahs)
        val searchView = view.findViewById<SearchView>(R.id.searchViewSurah)

        // Veriyi Repository'den al
        val surahList = SurahRepository.getAllSurahs()

        // Adapter'ı kur
        adapter = SurahAdapter(surahList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Arama Dinleyicisi
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Her harf yazıldığında filtrele
                adapter.filterList(newText ?: "")
                return true
            }
        })
    }
}