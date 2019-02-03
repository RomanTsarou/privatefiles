/*
 * Copyright (c) 2019 Roman Tsarou
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

package app.androld.privatefiles;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.graphics.Point;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;


/**
 * @author AndroLd
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class PrivateFilesProvider extends DocumentsProvider {
    public static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
    };
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS, Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES,
    };
    private static final String[] FIRST_PAGE_ITEMS_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_ICON,
            Document.COLUMN_FLAGS,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
    };
    private String ROOT_ID;
    private String APP_NAME;
    private int APP_ICON;

    @Override
    public boolean onCreate() {
        try {
            String packageName = getContext().getPackageName();
            PackageManager pm = getContext().getPackageManager();
            ROOT_ID = packageName + ".PrivateFilesProvider.ROOT";
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            APP_NAME = String.valueOf(pm.getApplicationLabel(appInfo));
            APP_ICON = appInfo.icon;
            if (APP_ICON == 0) {
                APP_ICON = android.R.drawable.sym_def_app_icon;
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }

    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(DEFAULT_ROOT_PROJECTION);
        final RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_ID);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_RECENTS);
        row.add(Root.COLUMN_TITLE, APP_NAME);
        row.add(Root.COLUMN_SUMMARY, "Private files");
        row.add(Root.COLUMN_ICON, APP_ICON);
        row.add(Root.COLUMN_MIME_TYPES, null);
        row.add(Root.COLUMN_AVAILABLE_BYTES, null);

        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId,
                                      String[] projection, String sortOrder) throws FileNotFoundException {
        if (ROOT_ID.equals(parentDocumentId)) {
            return getFirstPageItems();
        } else return getChildRows(parentDocumentId);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        // Create a cursor with the requested projection, or the default
        // projection.
        final MatrixCursor result = new MatrixCursor(
                DEFAULT_DOCUMENT_PROJECTION);
        if (ROOT_ID.equals(documentId)) {
            addRootRow(result);
        } else {
            addFileRow(new File(documentId), result);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(final String documentId,
                                             String mode, CancellationSignal signal)
            throws FileNotFoundException {
        final File file = new File(documentId);
        return ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId,
                                                     Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException {
        final File file = new File(documentId);
        final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file,
                ParcelFileDescriptor.MODE_READ_ONLY);

        return new AssetFileDescriptor(pfd, 0,
                AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    private void addRootRow(MatrixCursor result) {
        RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, ROOT_ID);
        row.add(Document.COLUMN_DISPLAY_NAME, APP_NAME);
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_FLAGS, null);
        row.add(Document.COLUMN_LAST_MODIFIED, null);
        row.add(Document.COLUMN_SIZE, null);
    }


    private Cursor getFirstPageItems() {
        Context context = getContext();
        final MatrixCursor result = new MatrixCursor(
                FIRST_PAGE_ITEMS_PROJECTION);
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, context.getFilesDir().getParentFile().getAbsolutePath());
        row.add(Document.COLUMN_DISPLAY_NAME, context.getPackageName());
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_ICON, APP_ICON);
        row.add(Document.COLUMN_FLAGS, null);
        row.add(Document.COLUMN_LAST_MODIFIED, null);
        row.add(Document.COLUMN_SIZE, null);
        return result;
    }

    private Cursor getChildRows(String parentDocumentId) {
        final MatrixCursor result = new MatrixCursor(
                FIRST_PAGE_ITEMS_PROJECTION);
        File folder = new File(parentDocumentId);
        for (File file : folder.listFiles()) {
            addFileRow(file, result);
        }
        return result;
    }

    private void addFileRow(File file, MatrixCursor result) {
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        String mime = getMimeType(file);
        row.add(Document.COLUMN_MIME_TYPE, mime);
        if (mime.startsWith("image/")) {
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL);
        } else {
            row.add(Document.COLUMN_FLAGS,
                    Document.FLAG_DIR_PREFERS_LAST_MODIFIED);
        }
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_SIZE, file.isDirectory() ? null : file.length());
    }

    private String getMimeType(File file) {
        if (file.isDirectory()) return Document.MIME_TYPE_DIR;
        String name = file.getName();
        int dotPos = name.lastIndexOf(".");
        if (dotPos == -1 || dotPos >= name.length() - 2 || dotPos == 0) {
            try {
                InputStream is = new BufferedInputStream(new FileInputStream(file));
                String fromStream = URLConnection.guessContentTypeFromStream(is);
                is.close();
                if (!TextUtils.isEmpty(fromStream)) {
                    return fromStream;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "application/octet-stream";
        }
        String extension = name.substring(dotPos + 1);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                extension.toLowerCase());
        return mime == null ? "application/octet-stream" : mime;
    }
}
