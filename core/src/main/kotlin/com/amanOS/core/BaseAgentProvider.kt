package com.amanOS.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri

abstract class BaseAgentProvider : ContentProvider() {

    protected lateinit var providerAuthority: String
    private lateinit var uriMatcher: UriMatcher

    override fun onCreate(): Boolean {
        providerAuthority = getAuthority()
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
        registerUris(uriMatcher)
        return onInitialize()
    }

    abstract fun getAuthority(): String
    abstract fun registerUris(matcher: UriMatcher)
    abstract fun onQuery(uri: Uri, code: Int, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor?
    abstract fun onInsert(uri: Uri, code: Int, values: ContentValues?): Uri?
    abstract fun onUpdate(uri: Uri, code: Int, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int
    abstract fun onDelete(uri: Uri, code: Int, selection: String?, selectionArgs: Array<String>?): Int

    open fun onInitialize(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? =
        onQuery(uri, uriMatcher.match(uri), projection, selection, selectionArgs, sortOrder)

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        onInsert(uri, uriMatcher.match(uri), values)

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int =
        onUpdate(uri, uriMatcher.match(uri), values, selection, selectionArgs)

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int =
        onDelete(uri, uriMatcher.match(uri), selection, selectionArgs)

    override fun getType(uri: Uri): String? = null

    protected fun noMatch(uri: Uri): Nothing =
        throw IllegalArgumentException("Unknown URI: $uri")
}
