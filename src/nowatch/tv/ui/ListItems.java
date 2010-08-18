package nowatch.tv.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import nowatch.tv.Main;
import nowatch.tv.R;
import nowatch.tv.service.UpdateTask;
import nowatch.tv.utils.DB;
import nowatch.tv.utils.Item;
import nowatch.tv.utils.Prefs;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class ListItems extends Activity implements OnItemClickListener {

    private static final String TAG = Main.TAG + "ItemsActivity";
    private final String REQ_ITEMS = "SELECT items._id, items.title, items.status, feeds.image, items.pubDate "
            + "FROM items INNER JOIN feeds ON items.feed_id=feeds._id "
            + "ORDER BY items.pubDate DESC LIMIT ";
    private final String REQ_MARK_ALL = "update items set status=" + Item.STATUS_UNREAD
            + " where status=" + Item.STATUS_NEW;
    private static final int MENU_MARK_ALL = 1;
    private static final int MENU_OPTIONS = 2;
    private static final int ITEMS_NB = 16;
    private int image_size;
    private ItemsAdapter adapter;
    private UpdateTaskBtn updateTask = null;
    private Context ctxt;
    private List<Item> items = null;
    private ListView list;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ctxt = getApplicationContext();
        setContentView(R.layout.items_activity);

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        image_size = (int) (48 * dm.density + 0.5f);

        // Title button
        ((ImageButton) findViewById(R.id.btn_logo)).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                resetList();
            }
        });
        // Menu buttons
        findViewById(R.id.btn_manage).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_refresh).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_refresh).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updateTask = new UpdateTaskBtn(ListItems.this);
                updateTask.execute();
            }
        });
        findViewById(R.id.btn_manage).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ListItems.this, Manage.class));
            }
        });

        // Set list adapter
        items = new ArrayList<Item>();
        adapter = new ItemsAdapter();
        list = (ListView) findViewById(R.id.list_items);
        list.setAdapter(adapter);
        list.setItemsCanFocus(false);
        list.setOnItemClickListener(this);
        list.setEmptyView(findViewById(R.id.list_empty));
        findViewById(R.id.loading).setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        // FIXME: Find better solution to refresh
        // Add existing items to list
        resetList();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // TODO: Handle configuration state save
        return super.onRetainNonConfigurationInstance();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_MARK_ALL, 0, R.string.menu_mark_all).setIcon(
                android.R.drawable.ic_menu_agenda);
        menu.add(0, MENU_OPTIONS, 0, R.string.menu_options).setIcon(
                android.R.drawable.ic_menu_preferences);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_MARK_ALL:
                SQLiteDatabase db = (new DB(ctxt)).getWritableDatabase();
                db.execSQL(REQ_MARK_ALL);
                db.close();
                return true;
            case MENU_OPTIONS:
                startActivity(new Intent(ListItems.this, Prefs.class));
                return true;
        }
        return false;
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent i = new Intent(ctxt, ItemInfo.class);
        i.putExtra(Item.EXTRA_ITEM_ID, items.get(position).id);
        startActivity(i);
    }

    private int addToList(int offset, int limit) {
        SQLiteDatabase db = null;
        Cursor c = null;
        byte[] logo_byte;
        int cnt = 0;
        try {
            db = (new DB(ctxt)).getWritableDatabase();
            c = db.rawQuery(REQ_ITEMS + offset + "," + limit, null);
            cnt = c.getCount();
            if (cnt > 0) {
                c.moveToFirst();
                do {
                    final Item item = new Item();
                    item.id = c.getInt(0);
                    item.title = c.getString(1);
                    // Status
                    switch (c.getInt(2)) {
                        case Item.STATUS_NEW:
                            item.status = getString(R.string.status_new);
                            break;
                        case Item.STATUS_DOWNLOADING:
                            item.status = getString(R.string.status_downloading);
                            break;
                        case Item.STATUS_UNREAD:
                            item.status = getString(R.string.status_unread);
                            break;
                        case Item.STATUS_READ:
                            item.status = getString(R.string.status_read);
                            break;
                        case Item.STATUS_DL_UNREAD:
                            item.status = getString(R.string.status_unread);
                            break;
                        case Item.STATUS_DL_READ:
                            item.status = getString(R.string.status_read);
                            break;
                        default:
                            item.status = getString(R.string.status_new);
                    }
                    // Icon
                    logo_byte = c.getBlob(3);
                    if (logo_byte != null && logo_byte.length > 200) {
                        item.logo = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(
                                logo_byte, 0, logo_byte.length), image_size, image_size, true);
                    } else {
                        item.logo = BitmapFactory.decodeResource(getResources(), R.drawable.icon);
                    }
                    // Date
                    long date = c.getLong(4);
                    long diff = System.currentTimeMillis() / 1000 - date / 1000;
                    if (diff < 3600) { // 1h
                        item.date = getString(R.string.date_hour);
                    } else if (diff < 86400) { // 24h
                        item.date = String.format(getString(R.string.date_hours), (diff / 60 / 60));
                    } else if (diff < 2678400) { // 31 days
                        item.date = String.format(getString(R.string.date_days),
                                (diff / 60 / 60 / 24));
                        /*
                         * } else if (diff < 7776000) { // 3 monthes item.date =
                         * String.format(getString(R.string.date_monthes), (diff
                         * / 60 / 60 / 24 / 30));
                         */
                    } else {
                        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");
                        formatter.setTimeZone(TimeZone.getDefault());
                        item.date = formatter.format(new Date(date));
                    }
                    // Actions
                    // item.action = new View.OnClickListener() {
                    // public void onClick(View v) {
                    // Intent i = new Intent(ctxt, InfoActivity.class);
                    // i.putExtra("item_id", item.id);
                    // startActivity(i);
                    // }
                    // };

                    // Add the item
                    items.add(item);
                } while (c.moveToNext());
            }
        } catch (SQLiteDiskIOException e) {
            // sqlite_stmt_journals partition is too small (4MB)
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        } catch (SQLiteException e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (c != null) {
                c.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return cnt;
    }

    public void resetList() {
        if (updateTask != null) {
            updateTask.cancel(true);
        }
        items.clear();
        adapter.clear();
        addToList(0, ITEMS_NB);
        updateList();
        list.setSelection(0);
    }

    private void updateList() {
        int len = items.size();
        for (int i = adapter.getCount(); i < len; i++) {
            adapter.add(null);
        }
    }

    static class ViewHolder {
        TextView title;
        TextView status;
        TextView date;
        ImageView logo;
        // ImageButton action;
    }

    private class ItemsAdapter extends ArrayAdapter<Item> implements Filterable {

        private LayoutInflater inflater;

        public ItemsAdapter() {
            super(ctxt, R.layout.list_items, R.id.title);
            inflater = LayoutInflater.from(ctxt);
        }

        @Override
        public Filter getFilter() {
            // Log.v(TAG, "getFilter()");
            return super.getFilter();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder vh;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_items, parent, false);
                vh = new ViewHolder();
                vh.title = (TextView) convertView.findViewById(R.id.title);
                vh.status = (TextView) convertView.findViewById(R.id.status);
                vh.date = (TextView) convertView.findViewById(R.id.date);
                vh.logo = (ImageView) convertView.findViewById(R.id.logo);
                // vh.action = (ImageButton)
                // convertView.findViewById(R.id.btn_actions);
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            // Set information
            final Item item = items.get(position);
            vh.title.setText(item.title);
            vh.status.setText(item.status);
            vh.date.setText(item.date);
            vh.logo.setImageBitmap(item.logo);
            // vh.action.setOnClickListener(item.action);
            // Set endless loader
            if (position == items.size() - 3) {
                new EndlessTask().execute(position + 1);
            }
            return convertView;
        }
    }

    private static class UpdateTaskBtn extends UpdateTask {

        public UpdateTaskBtn(ListItems a) {
            super(a);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mActivity != null) {
                Button btn_ref = (Button) getActivity().findViewById(R.id.btn_refresh);
                btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh_a, 0, 0, 0);
                ((AnimationDrawable) btn_ref.getCompoundDrawables()[0]).start();
                btn_ref.setEnabled(false);
                btn_ref.setClickable(false);
            }
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            if (mActivity != null) {
                ListItems a = getActivity();
                Button btn_ref = (Button) a.findViewById(R.id.btn_refresh);
                btn_ref.setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0, 0);
                btn_ref.setEnabled(true);
                btn_ref.setClickable(true);
                a.findViewById(R.id.loading).setVisibility(View.INVISIBLE);
                a.resetList();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mActivity != null) {
                Button btn_refresh = (Button) getActivity().findViewById(R.id.btn_refresh);
                btn_refresh
                        .setCompoundDrawablesWithIntrinsicBounds(R.drawable.btn_refresh, 0, 0, 0);
                btn_refresh.setEnabled(true);
                btn_refresh.setClickable(true);
            }
        }

    }

    class EndlessTask extends AsyncTask<Integer, Void, Void> {

        @Override
        protected void onPreExecute() {
            ((TextView) findViewById(R.id.loading)).setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Integer... params) {
            addToList(params[0], ITEMS_NB);
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            updateList();
            ((TextView) findViewById(R.id.loading)).setVisibility(View.INVISIBLE);
        }

    }
}