package org.h2lab.frnewsreader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.h2lab.frnewsreader.NewsApplication
import org.h2lab.frnewsreader.data.Article
import org.h2lab.frnewsreader.data.NewsCategory
import org.h2lab.frnewsreader.data.NewsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val articles: Map<NewsCategory, List<Article>> = emptyMap(),
    val lastRefreshAt: Long? = null,
    val readIds: Set<String> = emptySet(),
)

sealed interface NewsMessage {
    data class Refreshed(val newArticles: Int, val failedSources: List<String>) : NewsMessage
    data object Offline : NewsMessage
}

class NewsViewModel(private val repository: NewsRepository) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val messageChannel = Channel<NewsMessage>(Channel.BUFFERED)
    val messages: Flow<NewsMessage> = messageChannel.receiveAsFlow()

    val uiState: StateFlow<NewsUiState> = combine(repository.snapshot, refreshing) { snapshot, isRefreshing ->
        NewsUiState(
            isLoading = snapshot == null,
            isRefreshing = isRefreshing,
            articles = snapshot?.articles.orEmpty().groupBy { it.category },
            lastRefreshAt = snapshot?.lastRefreshAt,
            readIds = snapshot?.readIds.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsUiState())

    init {
        viewModelScope.launch { repository.load() }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val result = repository.refresh()
                val message = if (result.failedSources.size == result.totalSources) {
                    NewsMessage.Offline
                } else {
                    NewsMessage.Refreshed(result.newArticles, result.failedSources)
                }
                messageChannel.send(message)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                messageChannel.send(NewsMessage.Offline)
            } finally {
                refreshing.value = false
            }
        }
    }

    fun onArticleOpened(article: Article) {
        viewModelScope.launch { repository.markRead(article.id) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { NewsViewModel((this[APPLICATION_KEY] as NewsApplication).repository) }
        }
    }
}
