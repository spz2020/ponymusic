package me.wcy.music.discover.playlist.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.blankj.utilcode.util.SizeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.wcy.common.ext.loadAvatar
import me.wcy.common.ext.viewBindings
import me.wcy.common.utils.StatusBarUtils
import me.wcy.music.R
import me.wcy.music.account.service.UserService
import me.wcy.music.common.BaseMusicFragment
import me.wcy.music.common.OnItemClickListener2
import me.wcy.music.common.bean.SongData
import me.wcy.music.common.dialog.songmenu.SongMoreMenuDialog
import me.wcy.music.common.dialog.songmenu.items.AlbumMenuItem
import me.wcy.music.common.dialog.songmenu.items.ArtistMenuItem
import me.wcy.music.common.dialog.songmenu.items.CollectMenuItem
import me.wcy.music.common.dialog.songmenu.items.CommentMenuItem
import me.wcy.music.common.dialog.songmenu.items.DeletePlaylistSongMenuItem
import me.wcy.music.consts.RoutePath
import me.wcy.music.databinding.FragmentPlaylistDetailBinding
import me.wcy.music.databinding.ItemPlaylistTagBinding
import me.wcy.music.discover.playlist.detail.item.PlaylistSongItemBinder
import me.wcy.music.discover.playlist.detail.viewmodel.PlaylistViewModel
import me.wcy.music.service.AudioPlayer
import me.wcy.music.utils.ConvertUtils
import me.wcy.music.utils.ImageUtils.loadCover
import me.wcy.music.utils.toEntity
import me.wcy.radapter3.RAdapter
import me.wcy.router.annotation.Route
import javax.inject.Inject

/**
 * Created by wangchenyan.top on 2023/9/22.
 */
@Route(RoutePath.PLAYLIST_DETAIL)
@AndroidEntryPoint
class PlaylistDetailFragment : BaseMusicFragment() {
    private val viewBinding by viewBindings<FragmentPlaylistDetailBinding>()
    private val viewModel by viewModels<PlaylistViewModel>()
    private val adapter by lazy {
        RAdapter<SongData>()
    }

    @Inject
    lateinit var userService: UserService

    @Inject
    lateinit var audioPlayer: AudioPlayer

    override fun getRootView(): View {
        return viewBinding.root
    }

    override fun isUseLoadSir(): Boolean {
        return true
    }

    override fun getLoadSirTarget(): View {
        return viewBinding.coordinatorLayout
    }

    override fun onReload() {
        super.onReload()
        loadData()
    }

    override fun onLazyCreate() {
        super.onLazyCreate()

        val id = getRouteArguments().getLongExtra("id", 0)
        if (id <= 0) {
            finish()
            return
        }

        viewModel.init(id)

        initTitle()
        initPlaylistInfo()
        initSongList()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            showLoadSirLoading()
            val res = viewModel.loadData()
            if (res.isSuccess()) {
                showLoadSirSuccess()
            } else {
                showLoadSirError(res.msg)
            }
        }
    }

    private fun initTitle() {
        StatusBarUtils.getStatusBarHeight(requireActivity()) {
            (viewBinding.titlePlaceholder.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = it
                viewBinding.titlePlaceholder.requestLayout()
            }
            viewBinding.toolbarPlaceholder.layoutParams.height =
                requireContext().resources.getDimensionPixelSize(R.dimen.common_title_bar_size) + it
            viewBinding.toolbarPlaceholder.requestLayout()
        }

        viewBinding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            getTitleLayout()?.updateScroll(-verticalOffset)
        }
    }

    private fun initPlaylistInfo() {
        lifecycleScope.launch {
            viewModel.playlistData.collectLatest { playlistData ->
                if (playlistData != null) {
                    getTitleLayout()?.setTitleText(playlistData.name)
                    viewBinding.ivCover.loadCover(playlistData.getSmallCover(), SizeUtils.dp2px(6f))
                    viewBinding.tvPlayCount.text =
                        ConvertUtils.formatPlayCount(playlistData.playCount)
                    viewBinding.tvName.text = playlistData.name
                    viewBinding.ivCreatorAvatar.loadAvatar(playlistData.creator.avatarUrl)
                    viewBinding.tvCreatorName.text = playlistData.creator.nickname

                    viewBinding.flTags.removeAllViews()
                    playlistData.tags.forEach { tag ->
                        ItemPlaylistTagBinding.inflate(
                            LayoutInflater.from(context),
                            viewBinding.flTags,
                            true
                        ).apply {
                            root.text = tag
                        }
                    }

                    viewBinding.tvDesc.text = playlistData.description
                }
            }
        }
    }

    private fun initSongList() {
        viewBinding.llPlayAll.setOnClickListener {
            val songList = viewModel.songList.value.map { it.toEntity() }
            if (songList.isNotEmpty()) {
                audioPlayer.replaceAll(songList, songList.first())
            }
        }

        adapter.register(PlaylistSongItemBinder(object : OnItemClickListener2<SongData> {
            override fun onItemClick(item: SongData, position: Int) {
                val songList = viewModel.songList.value.map { it.toEntity() }
                if (songList.isNotEmpty()) {
                    audioPlayer.replaceAll(songList, songList[position])
                }
            }

            override fun onMoreClick(item: SongData, position: Int) {
                val items = mutableListOf(
                    CollectMenuItem(lifecycleScope, item),
                    CommentMenuItem(item),
                    ArtistMenuItem(item),
                    AlbumMenuItem(item)
                )
                val playlistData = viewModel.playlistData.value
                if (playlistData != null && playlistData.creator.userId == userService.getUserId()) {
                    items.add(DeletePlaylistSongMenuItem(playlistData, item))
                }
                SongMoreMenuDialog(requireActivity(), item)
                    .setItems(items)
                    .show()
            }
        }))
        viewBinding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.songList.collectLatest { songList ->
                adapter.refresh(songList)
            }
        }
    }

    override fun getNavigationBarColor(): Int {
        return R.color.play_bar_bg
    }
}