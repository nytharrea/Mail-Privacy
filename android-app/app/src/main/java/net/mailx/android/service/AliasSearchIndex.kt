package net.mailx.android.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.appsearch.app.AppSearchSchema
import androidx.appsearch.app.GenericDocument
import androidx.appsearch.app.PutDocumentsRequest
import androidx.appsearch.app.SearchSpec
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.platformstorage.PlatformStorage
import androidx.concurrent.futures.await
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mailx.android.R
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.ui.MainActivity
import net.mailx.android.ui.model.UiAlias

/** Explicitly opted-in, device-local AppSearch and launcher shortcuts. */
object AliasSearchIndex {
    private const val DB = "mailx_aliases"
    private const val NS = "aliases"
    private const val TYPE = "Alias"

    suspend fun replace(context: Context, aliases: List<UiAlias>) = withContext(Dispatchers.IO) {
        if (!EncryptedPrefsManager(context).aliasSearchEnabled) return@withContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val searchContext = PlatformStorage.SearchContext.Builder(context, DB).build()
                val session = PlatformStorage.createSearchSessionAsync(searchContext).await()
                try {
                    val schema = AppSearchSchema.Builder(TYPE)
                        .addProperty(AppSearchSchema.StringPropertyConfig.Builder("email")
                            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                            .build())
                        .build()
                    session.setSchemaAsync(SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        .setSchemaTypeDisplayedBySystem(TYPE, true)
                        .setForceOverride(true).build()).await()
                    // Remove deleted/renamed aliases before repopulating the opted-in index.
                    session.removeAsync("", SearchSpec.Builder().addFilterNamespaces(NS).build()).await()
                    for (batch in aliases.filter { it.deletedAt.isBlank() }.chunked(100)) {
                        val request = PutDocumentsRequest.Builder()
                        batch.forEach { alias ->
                            request.addGenericDocuments(
                                GenericDocument.Builder<GenericDocument.Builder<*>>(NS, alias.id, TYPE)
                                    .setPropertyString("email", alias.name).build()
                            )
                        }
                        session.putAsync(request.build()).await()
                    }
                } finally { session.close() }
            }
        }
        runCatching {
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtMost(4)
            val shortcuts = aliases.filter { it.deletedAt.isBlank() && it.enabled }
                .take(max).map { alias ->
                    val intent = Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra(AliasWatchWorker.EXTRA_WATCHED_ALIAS_ID, alias.id)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    ShortcutInfoCompat.Builder(context, "mailx_alias_${alias.id}")
                        .setShortLabel(alias.name.take(40))
                        .setLongLabel(alias.name.take(80))
                        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_mailx_web_mark))
                        .setIntent(intent).build()
                }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val searchContext = PlatformStorage.SearchContext.Builder(context, DB).build()
                val session = PlatformStorage.createSearchSessionAsync(searchContext).await()
                try {
                    session.removeAsync("", SearchSpec.Builder().addFilterNamespaces(NS).build()).await()
                } finally { session.close() }
            }
        }
    }
}
