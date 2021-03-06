package net.nowatch.ui;

import java.util.ArrayList;
import java.util.List;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.nowatch.service.INotifService;
import net.nowatch.service.INotifServiceCallback;
import net.nowatch.Main;
import net.nowatch.R;
import net.nowatch.service.NotifService;
import net.nowatch.utils.Db;
import net.nowatch.utils.Item;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class Manage extends Activity {

    private final String TAG = Main.TAG + "Manage";
    private static LayoutInflater mInflater;
    private DlAdapter adapterCurrent = null;
    private DlAdapter adapterPending = null;
    private List<Item> downloadCurrent;
    private List<Item> downloadPending;
    private int image_size;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(Manage.this, NotifService.class));
        setContentView(R.layout.activity_manage);
        mInflater = LayoutInflater.from(getApplicationContext());

        // Buttons
        findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        // Screen metrics (for dip to px conversion)
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        image_size = (int) (48 * dm.density + 0.5f);

        // Empty list
        ListView listCurrent = (ListView) findViewById(R.id.list_current);
        ListView listPending = (ListView) findViewById(R.id.list_pending);
        listCurrent.setEmptyView(findViewById(R.id.list_current_empty));
        listPending.setEmptyView(findViewById(R.id.list_pending_empty));
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(Manage.this, NotifService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
    }

    private OnItemClickListener listenerCurrent = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            DialogActions(v, position, NotifService.TYPE_CURRENT);
        }
    };
    private OnItemClickListener listenerPending = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
            DialogActions(v, position, NotifService.TYPE_PENDING);
        }
    };

    private void pauseDialog(final int position) {
        // Send intent to service
        Item item = downloadCurrent.get(position);
        final Context ctxt = Manage.this;
        Intent intent = new Intent(ctxt, NotifService.class);
        intent.setAction(NotifService.ACTION_PAUSE);
        intent.putExtra(Item.EXTRA_ITEM_ID, item.id);
        startService(intent);
        adapterCurrent.remove(item);
    }

    private void cancelDialog(final int position, final int type) {
        // TODO: Create a context menu and propose cancel and pause actions
        // Cancel will remove the file, pause will change state to INCOMPLETE
        final Context ctxt = Manage.this;
        final AlertDialog.Builder dialog = new AlertDialog.Builder(ctxt);
        dialog.setMessage(R.string.dialog_cancel);
        dialog.setPositiveButton(R.string.btn_yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                Item item = null;
                // Remove item from list
                if (type == NotifService.TYPE_CURRENT) {
                    item = downloadCurrent.get(position);
                    adapterCurrent.remove(item);
                } else if (type == NotifService.TYPE_PENDING) {
                    item = downloadPending.get(position);
                    adapterPending.remove(item);
                }
                // Send intent to service
                Intent intent = new Intent(ctxt, NotifService.class);
                intent.setAction(NotifService.ACTION_CANCEL);
                intent.putExtra(Item.EXTRA_ITEM_ID, item.id);
                intent.putExtra(Item.EXTRA_ITEM_TYPE, type);
                startService(intent);
            }
        });
        dialog.setNegativeButton(R.string.btn_no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        dialog.show();
    }

    private void DialogActions(final View view, final int position, final int type) {
        final Resources res = getResources();
        final QuickAction qa = new QuickAction(view);
        qa.setAnimStyle(QuickAction.ANIM_GROW_FROM_CENTER);

        if (type == NotifService.TYPE_CURRENT) {
            final ActionItem pause = new ActionItem();
            pause.setTitle(getString(R.string.dialog_pause_title));
            pause.setIcon(res.getDrawable(R.drawable.action_pause));
            pause.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pauseDialog(position);
                    qa.dismiss();
                }
            });
            qa.addActionItem(pause);
        }

        final ActionItem cancel = new ActionItem();
        cancel.setTitle(getString(R.string.dialog_cancel_title));
        cancel.setIcon(res.getDrawable(R.drawable.action_cancel));
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                cancelDialog(position, type);
                qa.dismiss();
            }
        });

        qa.addActionItem(cancel);
        qa.show();
    }

    private List<Item> getDownloads(int[] data) {
        SQLiteDatabase db = (new Db(getApplicationContext())).openDb();
        List<Item> list = new ArrayList<Item>();
        int len = data.length;
        for (int i = 0; i < len; i++) {
            Cursor c = db
                    .rawQuery(
                            "select items.title, feeds.image from items inner join feeds on items.feed_id=feeds._id where items._id="
                                    + data[i] + " limit 1", null);
            c.moveToFirst();
            if (c.getCount() > 0) {
                Item item = new Item();
                item.id = data[i];
                item.title = c.getString(0);
                byte[] logo_byte = c.getBlob(1);
                if (logo_byte != null && logo_byte.length > 200) {
                    item.logo = Bitmap.createScaledBitmap(BitmapFactory.decodeByteArray(logo_byte,
                            0, logo_byte.length), image_size, image_size, true);
                } else {
                    item.logo = BitmapFactory.decodeResource(getResources(), R.drawable.icon);
                }
                list.add(item);
            }
            c.close();
        }
        db.close();
        return list;
    }

    private void populateLists() {
        try {
            if (mService != null) {
                // Get data
                downloadCurrent = getDownloads(mService._getCurrentDownloads());
                downloadPending = getDownloads(mService._getPendingDownloads());

                Log.i(TAG, "downloadCurrent=" + downloadCurrent.size());
                Log.i(TAG, "downloadPending=" + downloadPending.size());

                // Create adapters
                final Context ctxt = getApplicationContext();
                adapterCurrent = new DlAdapter(ctxt, downloadCurrent);
                adapterPending = new DlAdapter(ctxt, downloadPending);
                ListView listCurrent = (ListView) findViewById(R.id.list_current);
                ListView listPending = (ListView) findViewById(R.id.list_pending);
                listCurrent.setAdapter(adapterCurrent);
                listPending.setAdapter(adapterPending);
                listCurrent.setOnItemClickListener(listenerCurrent);
                listPending.setOnItemClickListener(listenerPending);
            }
        } catch (RemoteException e) {
            if (e.getMessage() != null) {
                Log.e(TAG, e.getMessage());
            } else {
                Log.e(TAG, "RemoteException");
            }
        }
    }

    /**
     * Service Binding
     */
    private INotifService mService = null;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = INotifService.Stub.asInterface(service);
            if (mService != null) {
                try {
                    mService._registerCallback(mCallback);
                    populateLists();
                } catch (RemoteException e) {
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.toast_service_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            try {
                mService._unregisterCallback(mCallback);
            } catch (RemoteException e) {
            } finally {
                mService = null;
                Toast.makeText(getApplicationContext(), R.string.toast_service_disconnected,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private INotifServiceCallback mCallback = new INotifServiceCallback.Stub() {
        public void _valueChanged() {
            runOnUiThread(new Runnable() {
                public void run() {
                    populateLists();
                }
            });
        }
    };

    /**
     * Adapters
     */

    static class ViewHolder {
        ImageView logo;
        TextView title;
    }

    private static class DlAdapter extends ArrayAdapter<Item> {

        private List<Item> items;

        public DlAdapter(Context ctxt, List<Item> items) {
            super(ctxt, R.layout.list_download, R.id.title, items);
            this.items = items;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_download, null);
                holder = new ViewHolder();
                holder.logo = (ImageView) convertView.findViewById(R.id.logo);
                holder.title = (TextView) convertView.findViewById(R.id.title);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Item item = items.get(position);
            holder.title.setText(item.title);
            holder.logo.setImageBitmap(item.logo);
            return convertView;
        }
    }
}
