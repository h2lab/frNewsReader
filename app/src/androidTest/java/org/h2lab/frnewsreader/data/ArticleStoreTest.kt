package org.h2lab.frnewsreader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArticleStoreTest {
    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "store-test.json")

    @After
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun savesAndRestoresSnapshot() {
        val snapshot = NewsSnapshot(
            articles = listOf(
                Article("FRANCE|https://a", "lemonde", "Le Monde", NewsCategory.FRANCE, "Titre « é »", "Résumé", "https://a", "https://img/a.jpg", 1_000L),
                Article("TECHNOLOGIES|https://b", "cnews", "CNews", NewsCategory.TECHNOLOGIES, "Titre 2", "", "https://b", null, 2_000L),
            ),
            lastRefreshAt = 3_000L,
            readIds = setOf("FRANCE|https://a"),
        )
        ArticleStore(file).save(snapshot)
        assertEquals(snapshot, ArticleStore(file).load())
    }

    @Test
    fun returnsEmptySnapshotForMissingOrCorruptedFile() {
        assertEquals(NewsSnapshot(), ArticleStore(file).load())
        file.writeText("{corrompu")
        assertEquals(NewsSnapshot(), ArticleStore(file).load())
    }

    @Test
    fun loadingRepositoryRemovesDisabledSourcesFromCache() = runBlocking {
        val kept = Article("kept", "lemonde", "Le Monde", NewsCategory.FRANCE, "Titre", "", "https://a", null, 1_000L)
        val removed = kept.copy(id = "removed", sourceId = "cnews", sourceName = "CNews")
        val store = ArticleStore(file)
        store.save(NewsSnapshot(listOf(kept, removed), 3_000L, setOf(kept.id, removed.id)))

        val repository = NewsRepository(store, OkHttpClient())
        repository.load()

        val expected = NewsSnapshot(listOf(kept), 3_000L, setOf(kept.id))
        assertEquals(expected, repository.snapshot.value)
        assertEquals(expected, store.load())
    }
}
