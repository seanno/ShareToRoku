package com.shutdownhook.sharetoroku;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.shutdownhook.sharetoroku.roku.Roku;
import com.shutdownhook.sharetoroku.util.Loggy;

import java.util.List;

public class ShareToRokuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.d("ShareToRokuActivity lifecycle: onCreate");
        setContentView(R.layout.activity_share_to_roku);

        onNewIntent(getIntent());

        findViewById(R.id.searchButton).setOnClickListener(v -> doSearch(false));
        findViewById(R.id.keysButton).setOnClickListener(v -> doKeys());
        findViewById(R.id.backButton).setOnClickListener(v -> doCommand(Roku.Cmd.Back));
        findViewById(R.id.homeButton).setOnClickListener(v -> doCommand(Roku.Cmd.Home));
        findViewById(R.id.playButton).setOnClickListener(v -> doCommand(Roku.Cmd.Play));
        findViewById(R.id.rewindButton).setOnClickListener(v -> doCommand(Roku.Cmd.Rev));
        findViewById(R.id.forwardButton).setOnClickListener(v -> doCommand(Roku.Cmd.Fwd));

        ((CirclePad)findViewById(R.id.circlePad)).setListener(new CirclePad.Listener() {
            @Override public void onDirectional(CirclePad.Direction direction) {
                switch (direction) {
                    case UP: doCommand(Roku.Cmd.Up); break;
                    case DOWN: doCommand(Roku.Cmd.Down); break;
                    case LEFT: doCommand(Roku.Cmd.Left); break;
                    case RIGHT: doCommand(Roku.Cmd.Right); break;
                }
            }
            @Override public void onMiddleClick() {
                doCommand(Roku.Cmd.Select);
            }
            @Override public void onLongClick() {
                doCommand(Roku.Cmd.Backspace);
            }
        });

        channelAdapter = new ArrayAdapter<String>(this,
                R.layout.centered_text_item_layout);

        ListView list = findViewById(R.id.channelList);
        list.setAdapter(channelAdapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int position, long args) {
                doLaunch((Roku.ChannelInfo) a.getItemAtPosition(position));
            }
        });
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        log.d("ShareToRokuActivity lifecycle: onNewIntent");

        rokuName = getIntent().getStringExtra(ChooseRokuActivity.EXTRA_ROKU_NAME);
        rokuUrl = getIntent().getStringExtra(ChooseRokuActivity.EXTRA_ROKU_URL);
        getSupportActionBar().setTitle(rokuName == null ? "" : rokuName);

        String inboundSearch = intent.getStringExtra(ChooseRokuActivity.EXTRA_INBOUND_SEARCH);
        inboundSearch = (inboundSearch == null ? "" : Roku.sanitizeSearchString(inboundSearch));

        EditText searchBox = (EditText) findViewById(R.id.searchText);
        searchBox.setText(inboundSearch);

        if (!inboundSearch.isEmpty()) doSearch(true);

        populateChannels();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.d("ShareToRokuActivity lifecycle: onDestroy");
    }

    private void doSearch(boolean showToast) {
        String s = ((EditText)findViewById(R.id.searchText)).getText().toString();
        Roku.sendSearch(rokuUrl, s, new Roku.CmdResult() {
           @Override public void success() {
               String msg = getString(R.string.send_success);
               log.i(msg); if (showToast) toasty(msg);
           }
           @Override public void error(Exception e) {
               String msg = getString(R.string.send_failure_search) + " (" + e.toString() + ")";
               log.e(msg); // toasty(msg);
           }
        });
    }

    private void doKeys() {
        String s = ((EditText)findViewById(R.id.searchText)).getText().toString();
        Roku.sendString(rokuUrl, s, new Roku.CmdResult() {
            @Override public void success() { log.i("send string: %s", s); }
            @Override public void error(Exception e) {
                String msg = "Failed sending string to Roku" +
                        " " + s + " (" + e.toString() + ")";
                log.e(msg); // toasty(msg);
            }
        });
    }

    private void doCommand(Roku.Cmd cmd) {
        Roku.sendCmd(rokuUrl, cmd, new Roku.CmdResult() {
            @Override public void success() {
                log.i("sent command: %s", cmd.toString());
            }
            @Override public void error(Exception e) {
                String msg = getString(R.string.send_failure_cmd) +
                        " " + cmd.toString() + " (" + e.toString() + ")";
                log.e(msg); // toasty(msg);
            }
        });

    }

    private void doLaunch(Roku.ChannelInfo channel) {
        Roku.openChannel(rokuUrl, channel.ProviderId, new Roku.CmdResult() {
            @Override public void success() {
                log.i("launched: %s/%s", channel.Name, channel.ProviderId);
            }
            @Override public void error(Exception e) {
                String msg = getString(R.string.send_failure_launch) +
                        " (" + e.toString() + ")";
                log.e(msg); // toasty(msg);
            }
        });

    }

    private void populateChannels() {

        Roku.getChannels(rokuUrl, new Roku.ChannelsHandler() {
            @Override
            public void handle(List<Roku.ChannelInfo> channels) {
                channelAdapter.clear();
                channelAdapter.addAll(channels);
                channelAdapter.notifyDataSetChanged();
            }
            @Override
            public void error(Exception e) {
                String msg = getString(R.string.channels_failure) + " (" + e.toString() + ")";
                log.e(msg); // toasty(msg);
            }
        });

    }

    private void toasty(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    private String rokuName;
    private String rokuUrl;
    private ArrayAdapter channelAdapter;

    private final static Loggy log = new Loggy(ShareToRokuActivity.class.getName());
}