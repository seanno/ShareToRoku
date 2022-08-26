package com.shutdownhook.sharetoroku;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Comparator;
import java.util.Set;

import com.shutdownhook.sharetoroku.R;
import com.shutdownhook.sharetoroku.roku.Roku;
import com.shutdownhook.sharetoroku.util.ActivityWorker;
import com.shutdownhook.sharetoroku.util.Loggy;

public class ChooseRokuActivity extends AppCompatActivity {

    public static final String EXTRA_INBOUND_SEARCH = "com.shutdownhook.sharetoroku.SEARCH";
    public static final String EXTRA_ROKU_NAME = "com.shutdownhook.sharetoroku.ROKU_NAME";
    public static final String EXTRA_ROKU_URL = "com.shutdownhook.sharetoroku.ROKU_URL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.d("ChooseRokuActivity lifecycle: onCreate");
        setContentView(R.layout.activity_choose_roku);

        rokuAdapter = new ArrayAdapter<Roku.RokuInfo>(this,
                R.layout.centered_text_item_layout);

        ListView list = (ListView) findViewById(R.id.rokuList);
        list.setAdapter(rokuAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int position, long args) {
                Roku.RokuInfo roku = (Roku.RokuInfo) a.getItemAtPosition(position);
                if (roku.Url == null) {
                    enableSpinner(true);
                    findRokus();
                } else {
                    shareToRoku(roku);
                }
            }
        });

        SwipeRefreshLayout swipe = (SwipeRefreshLayout) findViewById(R.id.swipeRefresh);
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
           @Override
           public void onRefresh() {
               findRokus();
           }
        });

        ((TextView)findViewById(R.id.privacyLink))
                .setMovementMethod(LinkMovementMethod.getInstance());

        initializeRokus();
        onNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        log.d("ChooseRokuActivity lifecycle: onNewIntent");

        String action = intent.getAction();
        String type = intent.getType();
        log.d("Inbound action=%s, type=%s", action, type);

        if (Intent.ACTION_SEND.equals(action)) {
            // the type, it seems, is kind of BS. E.g., TV Time sends "image/*" but then
            // includes a link to the show in the extra text. So just look and see if there
            // is freaking anything there. Really, people.
            inboundSearch = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (inboundSearch != null && inboundSearch.trim().isEmpty()) inboundSearch = null;
        }
        else {
            inboundSearch = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.d("ChooseRokuActivity lifecycle: onDestroy");
        if (activityWorker != null) activityWorker.close();
    }

    private void shareToRoku(Roku.RokuInfo roku) {
        log.d("Navigating to ShareToRokuActivity: %s", roku.toString());
        Roku.sendCmd(roku.Url, Roku.Cmd.PowerOn, null);
        Intent intent = new Intent(this, ShareToRokuActivity.class);
        intent.putExtra(EXTRA_ROKU_NAME, roku.Name);
        intent.putExtra(EXTRA_ROKU_URL, roku.Url);
        if (inboundSearch != null) intent.putExtra(EXTRA_INBOUND_SEARCH, inboundSearch);
        startActivity(intent);
    }

    private void initializeRokus() {
        Set<Roku.RokuInfo> rokus = Roku.getCachedRokus();
        if (rokus != null && rokus.size() > 0) {
            populateRokus(rokus);
        }
        else {
            enableSpinner(true);
            findRokus();
        }
    }

    private void findRokus() {

        if (activityWorker == null) {
            activityWorker = new ActivityWorker(this);
        }

        activityWorker.run(new ActivityWorker.Worker() {
            private Set<Roku.RokuInfo> rokus;
            public boolean doBackground() {
                rokus = Roku.getRokus();
                return(true);
            }
            public void doUx() {
                if (rokus != null) populateRokus(rokus);
            }
        });
    }

    private void populateRokus(Set<Roku.RokuInfo> rokus) {
        log.i("populating %d rokus", rokus.size());
        rokuAdapter.clear();

        if (rokus.size() == 0) {

            Roku.RokuInfo emptyInfo = new Roku.RokuInfo();
            emptyInfo.Name = getApplicationContext().getResources().getString(R.string.no_rokus);
            emptyInfo.Url = null; // redundant, just making clear this is a marker
            rokuAdapter.add(emptyInfo);
        }
        else {

            rokuAdapter.addAll(rokus);

            rokuAdapter.sort(new Comparator<Roku.RokuInfo>() {
                @Override
                public int compare(Roku.RokuInfo info1, Roku.RokuInfo info2) {
                    if (info1 == info2) return (0);
                    if (info1 == null) return (-1);
                    if (info2 == null) return (1);
                    int cmp = info1.Name.compareTo(info2.Name);
                    if (cmp == 0) cmp = info1.Url.compareTo(info2.Url);
                    return (cmp);
                }
            });
        }

        rokuAdapter.notifyDataSetChanged();
        enableSpinner(false);
    }

    private void enableSpinner(boolean enable) {
        ((SwipeRefreshLayout)findViewById(R.id.swipeRefresh)).setRefreshing(enable);
    }

    private ActivityWorker activityWorker;
    private ArrayAdapter rokuAdapter;
    private String inboundSearch;

    private final static Loggy log = new Loggy(ChooseRokuActivity.class.getName());
}
