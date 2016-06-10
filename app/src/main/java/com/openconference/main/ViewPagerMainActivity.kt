package com.openconference.main

import android.os.Bundle
import android.support.design.widget.TabLayout
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import butterknife.bindView
import com.openconference.R
import com.openconference.model.screen.Screens
import com.openconference.util.applicationComponent
import javax.inject.Inject

class ViewPagerMainActivity : AppCompatActivity() {

  @Inject lateinit var screens: Screens
  private val viewPager by bindView<ViewPager>(R.id.viewpager)
  private val tabs by bindView<TabLayout>(R.id.tabs)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    DaggerMainActivityComponent.builder()
        .applicationComponent(applicationComponent())
        .build()
        .inject(this)


    // Setup UI
    viewPager.setPageMarginDrawable(R.drawable.viewpager_separator)
    viewPager.pageMargin = resources.getDimensionPixelOffset(R.dimen.viewpager_page_margin)
    viewPager.adapter = MainScreensPagerAdapter(this, screens.screens)
    tabs.setupWithViewPager(viewPager)


  }
}