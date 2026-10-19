package fr.tpsarena.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.tpsarena.companion.ui.Resource
import fr.tpsarena.companion.ui.ResourceState
import fr.tpsarena.companion.ui.components.ErrorState
import fr.tpsarena.companion.ui.components.Loading

/** Scrolling screen body (`.app-main`): 16 dp margins, 14 dp between blocks. */
@Composable
fun ScreenList(modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Loading or error state while a resource has no data yet; [content] once it has some. */
@Composable
fun <T> WithData(resource: Resource<T>, content: @Composable (ResourceState<T>, T) -> Unit) {
    val state by resource.state.collectAsStateWithLifecycle()
    val data = state.data
    when {
        data != null -> content(state, data)
        state.loading -> Loading()
        else -> ErrorState(state.error ?: "Erreur inattendue.", onRetry = resource::reload)
    }
}
