package com.functions.goshop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private const val ARG_CATEGORY = "category"

class ProductListFragment : Fragment() {

    private var category: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getString(ARG_CATEGORY)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_product_list, container, false)
        val productsRecyclerView: RecyclerView = view.findViewById(R.id.productsRecyclerView)
        productsRecyclerView.layoutManager = GridLayoutManager(context, 2)

        // TODO: Use the 'category' variable to fetch and display the correct data.

        return view
    }

    // This is the essential part that was likely missing
    companion object {
        /**
         * Use this factory method to create a new instance of this fragment
         * using the provided parameters.
         *
         * @param category Parameter 1.
         * @return A new instance of fragment ProductListFragment.
         */
        fun newInstance(category: String) =
            ProductListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                }
            }
    }
}