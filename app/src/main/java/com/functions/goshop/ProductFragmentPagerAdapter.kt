package com.functions.goshop



import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ProductFragmentPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // The number of tabs/fragments you have
    override fun getItemCount(): Int = 4

    // This function creates the appropriate fragment for each tab position
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> TrendingFragment()
            2 -> LatestFragment()
            3 -> FavoritesFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}