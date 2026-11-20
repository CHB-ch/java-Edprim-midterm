/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Displays a list of notes. In addition to the original sample this version
 * adds: a modification timestamp on every row, full-text search (title or
 * body), sorting and category classification/filtering.
 */
public class NotesList extends ListActivity {

    private static final String TAG = "NotesList";

    /** The columns needed by the cursor adapter. */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                           // 0
            NotePad.Notes.COLUMN_NAME_TITLE,             // 1
            NotePad.Notes.COLUMN_NAME_CATEGORY,          // 2
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE  // 3
    };

    /** The index of the title column. */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** Categories offered by the filter dialog (index 0 means "all"). */
    private static final String[] CATEGORIES = {
            "全部", "默认", "学习", "工作", "生活", "重要"
    };

    /** Sort choices shown in the sort dialog. */
    private static final String[] SORT_LABELS = {
            "修改时间（最新优先）", "修改时间（最早优先）",
            "标题（A→Z）", "创建时间（最新优先）"
    };
    private static final String[] SORT_ORDERS = {
            "modified DESC", "modified ASC",
            "title COLLATE NOCASE ASC", "created DESC"
    };

    private SimpleCursorAdapter mAdapter;
    private String mSearchText = "";
    private String mCategoryFilter = null;   // null means no category filter
    private String mSortOrder = NotePad.Notes.DEFAULT_SORT_ORDER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        /*
         * Map the note title, category and modification date to the three
         * TextViews defined in noteslist_item.xml. A ViewBinder formats the
         * raw timestamp into a readable date for us.
         */
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };
        int[] viewIDs = { R.id.note_title, R.id.note_category, R.id.note_date };

        mAdapter = new SimpleCursorAdapter(
                this, R.layout.noteslist_item, null, dataColumns, viewIDs, 0);
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.note_date) {
                    long millis = cursor.getLong(columnIndex);
                    ((TextView) view).setText(formatTime(millis));
                    return true;
                }
                if (view.getId() == R.id.note_category) {
                    String category = cursor.getString(columnIndex);
                    if (TextUtils.isEmpty(category)) {
                        category = NotePad.Notes.DEFAULT_CATEGORY;
                    }
                    ((TextView) view).setText(category);
                    return true;
                }
                return false; // let the adapter bind the title itself
            }
        });
        setListAdapter(mAdapter);

        // Load the data (search / filter / sort state is applied here).
        refreshList();
    }

    /** Format a millisecond timestamp as "yyyy-MM-dd HH:mm". */
    private String formatTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }

    /**
     * (Re)query the provider applying the current search text, category filter
     * and sort order, then hand the new cursor to the adapter.
     */
    private void refreshList() {
        StringBuilder selection = new StringBuilder();
        ArrayList<String> args = new ArrayList<String>();

        if (!TextUtils.isEmpty(mSearchText)) {
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
            String like = "%" + mSearchText + "%";
            args.add(like);
            args.add(like);
        }
        if (mCategoryFilter != null) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            args.add(mCategoryFilter);
        }

        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection.length() > 0 ? selection.toString() : null,
                args.isEmpty() ? null : args.toArray(new String[0]),
                mSortOrder);
        mAdapter.swapCursor(cursor);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAdapter != null && mAdapter.getCursor() != null) {
            mAdapter.getCursor().close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // Wire up the SearchView in the action bar.
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        mSearchText = query == null ? "" : query.trim();
                        refreshList();
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        mSearchText = newText == null ? "" : newText.trim();
                        refreshList();
                        return true;
                    }
                });
            }
        }

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        MenuItem pasteItem = menu.findItem(R.id.menu_paste);
        if (pasteItem != null) {
            pasteItem.setEnabled(clipboard != null && clipboard.hasPrimaryClip());
        }

        final boolean haveItems = getListAdapter() != null && getListAdapter().getCount() > 0;
        if (haveItems) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());
            Intent[] specifics = new Intent[] { new Intent(Intent.ACTION_EDIT, uri) };
            MenuItem[] items = new MenuItem[1];
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, Menu.NONE, Menu.NONE,
                    null, specifics, intent, Menu.NONE, items);
            if (items[0] != null) {
                items[0].setShortcut('1', 'e');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.menu_category) {
            showCategoryDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Dialog that lets the user choose the sort order. */
    private void showSortDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_sort)
                .setItems(SORT_LABELS, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSortOrder = SORT_ORDERS[which];
                        refreshList();
                        Toast.makeText(NotesList.this, SORT_LABELS[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** Dialog that lets the user filter notes by category. */
    private void showCategoryDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_category)
                .setItems(CATEGORIES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mCategoryFilter = which == 0 ? null : CATEGORIES[which];
                        refreshList();
                        Toast.makeText(NotesList.this, CATEGORIES[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        int id = item.getItemId();
        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(
                        ClipData.newUri(getContentResolver(), "Note", noteUri));
            }
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            refreshList();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
