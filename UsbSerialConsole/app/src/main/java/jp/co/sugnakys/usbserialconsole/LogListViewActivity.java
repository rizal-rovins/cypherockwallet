package jp.co.sugnakys.usbserialconsole;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;

public class LogListViewActivity extends ListActivity
        implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {
    private static final String TAG = "LogListViewActivity";

    @Override
    public void onResume() {
        super.onResume();

        updateList();

        ListView listView = getListView();
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);
    }

    private boolean deleteLogFile(File file) {
        Log.d(TAG, "remove file path: " + file.getName());
        return file.delete();
    }

    private String[] getFileNameList() {
        File[] file = Util.getLogDir(getApplicationContext()).listFiles();
        if (file == null) {
            return null;
        }

        String[] fileName = new String[file.length];
        for (int i = 0; i < file.length; i++) {
            fileName[i] = file[i].getName();
        }
        return fileName;
    }

    private void updateList() {
        String[] files = getFileNameList();
        if (files == null) {
            return;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                files);
        setListAdapter(adapter);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        ListView listView = (ListView) adapterView;
        String selectedItem = (String) listView.getItemAtPosition(position);

        Context context = getApplicationContext();
        File targetFile = new File(Util.getLogDir(context), selectedItem);

        Intent intent = new Intent(getApplicationContext(), LogViewActivity.class);
        intent.putExtra(Constants.EXTRA_LOG_FILE, targetFile);
        startActivity(intent);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
        ListView listView = (ListView) adapterView;
        final String selectedItem = (String) listView.getItemAtPosition(position);
        new AlertDialog.Builder(LogListViewActivity.this)
                .setTitle(getResources().getString(R.string.remove_log_file_title))
                .setMessage(getResources().getString(R.string.remove_log_file_text) + "\n"
                        + getResources().getString(R.string.file_name) + ": " + selectedItem)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Context context = getApplicationContext();
                                File targetFile =
                                        new File(Util.getLogDir(context), selectedItem);
                                if (deleteLogFile(targetFile)) {
                                    updateList();
                                }
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        return true;
    }
}