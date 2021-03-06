package com.crazylegend.setofusefulkotlinextensions


import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import com.crazylegend.kotlinextensions.RunCodeEveryXLaunchOnAppOpened
import com.crazylegend.kotlinextensions.abstracts.AbstractViewBindingAdapterRxBus
import com.crazylegend.kotlinextensions.autoStart.AutoStartHelper
import com.crazylegend.kotlinextensions.autoStart.ConfirmationDialogAutoStart
import com.crazylegend.kotlinextensions.context.getCompatColor
import com.crazylegend.kotlinextensions.context.isGestureNavigationEnabled
import com.crazylegend.kotlinextensions.delegates.activityAVM
import com.crazylegend.kotlinextensions.exhaustive
import com.crazylegend.kotlinextensions.gestureNavigation.EdgeToEdge
import com.crazylegend.kotlinextensions.log.debug
import com.crazylegend.kotlinextensions.recyclerview.HideOnScrollListener
import com.crazylegend.kotlinextensions.recyclerview.RecyclerSwipeItemHandler
import com.crazylegend.kotlinextensions.recyclerview.addDrag
import com.crazylegend.kotlinextensions.recyclerview.addSwipe
import com.crazylegend.kotlinextensions.retrofit.retrofitResult.RetrofitResult
import com.crazylegend.kotlinextensions.rx.RxBus
import com.crazylegend.kotlinextensions.rx.bindings.textChanges
import com.crazylegend.kotlinextensions.rx.clearAndDispose
import com.crazylegend.kotlinextensions.security.encryptFileSafely
import com.crazylegend.kotlinextensions.security.getEncryptedFile
import com.crazylegend.kotlinextensions.security.readText
import com.crazylegend.kotlinextensions.transition.StaggerTransition
import com.crazylegend.kotlinextensions.transition.interpolators.FAST_OUT_SLOW_IN
import com.crazylegend.kotlinextensions.transition.utils.LARGE_EXPAND_DURATION
import com.crazylegend.kotlinextensions.transition.utils.plusAssign
import com.crazylegend.kotlinextensions.transition.utils.transitionSequential
import com.crazylegend.kotlinextensions.viewBinding.viewBinding
import com.crazylegend.kotlinextensions.views.AppRater
import com.crazylegend.setofusefulkotlinextensions.adapter.TestModel
import com.crazylegend.setofusefulkotlinextensions.adapter.TestPlaceHolderAdapter
import com.crazylegend.setofusefulkotlinextensions.adapter.TestViewBindingAdapter
import com.crazylegend.setofusefulkotlinextensions.databinding.ActivityMainBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.io.File

class MainAbstractActivity : AppCompatActivity() {

    private val testAVM by activityAVM<TestAVM>(arrayOf(TestModel("", 1, "", 2), 1, ""))

    private val testPlaceHolderAdapter by lazy {
        TestPlaceHolderAdapter()
    }
    private val generatedAdapter by lazy {
        TestViewBindingAdapter()
    }


    private val compositeDisposable by lazy {
        CompositeDisposable()
    }

    private val activityMainBinding by viewBinding(ActivityMainBinding::inflate)
    private var savedItemAnimator: RecyclerView.ItemAnimator? = null

    private val fade = transitionSequential {
        duration = LARGE_EXPAND_DURATION
        interpolator = FAST_OUT_SLOW_IN
        this += Fade(Fade.OUT)
        this += Fade(Fade.IN)
        addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                if (savedItemAnimator != null) {
                    activityMainBinding.recycler.itemAnimator = savedItemAnimator
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activityMainBinding.root)

        RxBus.listen<AbstractViewBindingAdapterRxBus.LongClick<TestModel>>().subscribe {
            val longClickedModel = it.data
            debug("LONG CLICKED MODEL $longClickedModel")
        }
        RxBus.listen<AbstractViewBindingAdapterRxBus.SingleClick<TestModel>>().subscribe {
            val longClickedModel = it.data
            debug("CLICKED MODEL $longClickedModel")
        }

        activityMainBinding.test.setOnClickListener {
            testAVM.getposts()
        }

        RunCodeEveryXLaunchOnAppOpened.runCode(this, 2) {
            debug("TEST RUN AT 2 LAUNCHES")
        }

        activityMainBinding.recycler.addOnScrollListener(object : HideOnScrollListener(5) {
            override fun onHide() {
                activityMainBinding.test.hide()
            }

            override fun onShow() {
                activityMainBinding.test.show()
            }
        })
        AppRater.appLaunched(this, supportFragmentManager, 0, 0) {
            appTitle = getString(R.string.app_name)
            buttonsBGColor = getCompatColor(R.color.colorAccent)
        }

        /*generatedAdapter.forItemClickListener = forItemClickListenerDSL { position, item, view ->
            debug("SADLY CLICKED HERE $item")
        }*/
        activityMainBinding.recycler.addSwipe(this) {
            swipeDirection = RecyclerSwipeItemHandler.SwipeDirs.BOTH
            drawableLeft = android.R.drawable.ic_delete
            drawLeftBackground = true
            leftBackgroundColor = R.color.colorPrimary
            drawableRight = android.R.drawable.ic_input_get
        }

        AutoStartHelper.checkAutoStart(this, dialogBundle = bundleOf(
                Pair(ConfirmationDialogAutoStart.CANCEL_TEXT, "Dismiss"),
                Pair(ConfirmationDialogAutoStart.CONFIRM_TEXT, "Allow"),
                Pair(ConfirmationDialogAutoStart.DO_NOT_SHOW_AGAIN_VISIBILITY, true)
        ))

        if (isGestureNavigationEnabled()) {
            EdgeToEdge.setUpRoot(activityMainBinding.root)
            EdgeToEdge.setUpScrollingContent(activityMainBinding.recycler)
        }

        val stagger = StaggerTransition()

        testAVM.posts.observe(this, Observer {
            it?.apply {
                when (it) {
                    is RetrofitResult.Success -> {
                        TransitionManager.beginDelayedTransition(activityMainBinding.recycler, stagger)
                        if (activityMainBinding.recycler.adapter != generatedAdapter) {
                            activityMainBinding.recycler.adapter = generatedAdapter
                            savedItemAnimator = activityMainBinding.recycler.itemAnimator
                            activityMainBinding.recycler.itemAnimator = null
                            TransitionManager.beginDelayedTransition(activityMainBinding.recycler, fade)
                        }
                        generatedAdapter.submitList(it.value)
                        val wrappedList = it.value.toMutableList()
                        activityMainBinding.recycler.addDrag(generatedAdapter, wrappedList)
                    }
                    RetrofitResult.Loading -> {
                        activityMainBinding.recycler.adapter = testPlaceHolderAdapter
                        debug(it.toString())
                    }
                    RetrofitResult.EmptyData -> {
                        debug(it.toString())
                    }
                    is RetrofitResult.Error -> {
                        debug(it.toString())
                    }
                    is RetrofitResult.ApiError -> {
                        debug(it.toString())
                    }
                }.exhaustive
            }
        })

        testAVM.filteredPosts.observe(this, Observer {
            generatedAdapter.submitList(it)
        })

        val testFile = File(filesDir, "testfile.txt")
        encryptFileSafely(testFile, fileContent = "JETPACK SECURITY".toByteArray())
        val file = getEncryptedFile(testFile)
        debug("TEXT DECRYPTED ${file.readText()}")
        debug("TEXT ENCRYPTED ${testFile.readText()}")
    }

    private var searchView: SearchView? = null

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu?.findItem(R.id.app_bar_search)

        searchItem?.apply {
            searchView = this.actionView as SearchView?
        }

        searchView?.queryHint = "Search by title"

        searchView?.textChanges(debounce = 1000L, compositeDisposable = compositeDisposable) {
            testAVM.filterBy(it)
        }

        return super.onCreateOptionsMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clearAndDispose()
    }

}


