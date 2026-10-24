package org.h2lab.frnewsreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.h2lab.frnewsreader.R
import org.h2lab.frnewsreader.data.Article
import org.h2lab.frnewsreader.data.NewsCategory
import org.h2lab.frnewsreader.data.RelativeTime
import org.h2lab.frnewsreader.data.SourceCatalog
import org.h2lab.frnewsreader.ui.theme.FrNewsReaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val sourceColors = SourceCatalog.sources.associate { it.id to Color(it.color) }
private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)
private val editionDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)

@Composable
fun NewsApp(viewModel: NewsViewModel = viewModel(factory = NewsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                NewsMessage.Offline -> resources.getString(R.string.refresh_offline)
                is NewsMessage.Refreshed -> buildString {
                    append(
                        if (message.newArticles == 0) resources.getString(R.string.refresh_no_new_articles)
                        else resources.getQuantityString(R.plurals.refresh_new_articles, message.newArticles, message.newArticles),
                    )
                    if (message.failedSources.isNotEmpty()) {
                        append('\n')
                        append(resources.getString(R.string.refresh_failed_sources, message.failedSources.joinToString(", ")))
                    }
                }
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    NewsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = viewModel::refresh,
        onArticleClick = { article ->
            viewModel.onArticleOpened(article)
            openArticle(context, article.link)
        },
    )
}

private fun openArticle(context: Context, link: String) {
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, link.toUri())
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_browser, Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    state: NewsUiState,
    onRefresh: () -> Unit,
    onArticleClick: (Article) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val categories = NewsCategory.entries
    val pagerState = rememberPagerState(pageCount = { categories.size })
    val scope = rememberCoroutineScope()
    val now = rememberCurrentTime()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing && !state.isLoading) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).testTag("refresh_progress"),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (state.isRefreshing) R.string.refresh_in_progress else R.string.action_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                stringResource(category.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }
            if (state.isRefreshing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val articles = state.articles[categories[page]].orEmpty()
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    articles.isEmpty() -> EmptyState(state.isRefreshing, onRefresh)
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        ArticleFeed(categories[page], articles, state, now, onArticleClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberCurrentTime(): Long {
    val time by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    return time
}

@Composable
private fun ArticleFeed(
    category: NewsCategory,
    articles: List<Article>,
    state: NewsUiState,
    now: Long,
    onArticleClick: (Article) -> Unit,
) {
    val sourceCount = articles.map { it.sourceId }.distinct().size
    LazyColumn(
        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("article_feed"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "last_update") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(
                    editionDateFormatter.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(category.labelRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    pluralStringResource(
                        R.plurals.edition_articles, articles.size, articles.size,
                        pluralStringResource(
                            R.plurals.edition_sources,
                            sourceCount,
                            sourceCount,
                        ),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.lastRefreshAt?.let { stringResource(R.string.last_update, relativeTimeText(it, now)) }
                        ?: stringResource(R.string.never_refreshed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = articles.first().id) {
            StoryCard(articles.first(), true, articles.first().id in state.readIds, now, onArticleClick)
        }
        if (articles.size > 1) {
            item(key = "latest_heading") {
                Text(
                    stringResource(R.string.latest_articles),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
        }
        items(articles.drop(1), key = { it.id }) { article ->
            StoryCard(article, false, article.id in state.readIds, now, onArticleClick)
        }
    }
}

@Composable
private fun StoryCard(
    article: Article,
    headline: Boolean,
    isRead: Boolean,
    now: Long,
    onArticleClick: (Article) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        if (headline) {
            HeadlineArticle(article, isRead, now, onArticleClick)
        } else {
            CompactArticle(article, isRead, now, onArticleClick)
        }
    }
}

@Composable
private fun HeadlineArticle(article: Article, isRead: Boolean, now: Long, onClick: (Article) -> Unit) {
    val readingState = stringResource(if (isRead) R.string.article_read else R.string.article_unread)
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { stateDescription = readingState }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_article)) { onClick(article) }
            .padding(16.dp),
    ) {
        if (article.imageUrl != null) {
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(12.dp))
        }
        SourceLine(article)
        Spacer(Modifier.height(6.dp))
        Text(
            text = article.title,
            style = MaterialTheme.typography.titleLarge,
            color = titleColor(isRead),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (article.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        ArticleMetadata(article.publishedAt, now, isRead)
    }
}

@Composable
private fun CompactArticle(article: Article, isRead: Boolean, now: Long, onClick: (Article) -> Unit) {
    val readingState = stringResource(if (isRead) R.string.article_read else R.string.article_unread)
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { stateDescription = readingState }
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_article)) { onClick(article) }
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            SourceLine(article)
            Spacer(Modifier.height(6.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor(isRead),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            ArticleMetadata(article.publishedAt, now, isRead)
        }
        if (article.imageUrl != null) {
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = article.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun titleColor(isRead: Boolean): Color =
    if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

@Composable
private fun SourceLine(article: Article) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(sourceColors[article.sourceId] ?: MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = article.sourceName.first { it.isLetter() }.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (article.sourceId == "franceinfo") Color.Black else Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = article.sourceName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArticleMetadata(publishedAt: Long, now: Long, isRead: Boolean) {
    Text(
        text = relativeTimeText(publishedAt, now) +
            if (isRead) stringResource(R.string.article_read_suffix) else "",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun relativeTimeText(time: Long, now: Long): String =
    when (val relative = RelativeTime.of(time, now)) {
        RelativeTime.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes, relative.value, relative.value)
        is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours, relative.value, relative.value)
        RelativeTime.Yesterday -> stringResource(R.string.time_yesterday)
        is RelativeTime.Days -> pluralStringResource(R.plurals.time_days, relative.value, relative.value)
        is RelativeTime.Date -> dateFormatter.format(Instant.ofEpochMilli(relative.epochMillis).atZone(ZoneId.systemDefault()))
    }

@Composable
private fun EmptyState(isRefreshing: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 3.dp)
        } else Icon(
            painter = painterResource(R.drawable.ic_article),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (isRefreshing) R.string.loading_articles else R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(if (isRefreshing) R.string.loading_message else R.string.empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRefresh, enabled = !isRefreshing) {
            Text(stringResource(R.string.action_refresh))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NewsScreenPreview() {
    val now = System.currentTimeMillis()
    val articles = listOf(
        Article("1", "lemonde", "Le Monde", NewsCategory.FRANCE, "Un titre d'actualité à la une", "Résumé de l'article.", "https://example.com/1", null, now - 600_000),
        Article("2", "franceinfo", "franceinfo", NewsCategory.FRANCE, "Un second titre", "", "https://example.com/2", null, now - 7_200_000),
    )
    FrNewsReaderTheme {
        NewsScreen(
            state = NewsUiState(isLoading = false, articles = mapOf(NewsCategory.FRANCE to articles), lastRefreshAt = now),
            onRefresh = {},
            onArticleClick = {},
        )
    }
}
