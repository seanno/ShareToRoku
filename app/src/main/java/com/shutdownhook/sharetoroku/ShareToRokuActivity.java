package com.shutdownhook.sharetoroku;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.shutdownhook.sharetoroku.roku.Roku;
import com.shutdownhook.sharetoroku.roku.SearchParser;
import com.shutdownhook.sharetoroku.util.Loggy;

import java.util.ArrayList;
import java.util.List;

public class ShareToRokuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        log.d("ShareToRokuActivity lifecycle: onCreate");
        setContentView(R.layout.activity_share_to_roku);

        onNewIntent(getIntent());

        findViewById(R.id.searchButton).setOnClickListener(v -> parseAndSearch(false));
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

        overrideParseInput = intent.getStringExtra(ChooseRokuActivity.EXTRA_INBOUND_SEARCH);
        String sanitizedInput = (overrideParseInput == null
                ? "" : SearchParser.sanitizeSearchString(overrideParseInput));

        EditText searchBox = (EditText) findViewById(R.id.searchText);
        searchBox.setText(sanitizedInput);

        lastParseInput = null;
        lastParseResult = null;

        populateChannels(overrideParseInput != null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        log.d("ShareToRokuActivity lifecycle: onDestroy");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent evt) {

        boolean ret = true;

        switch (keyCode) {

            case KeyEvent.KEYCODE_VOLUME_UP:   doCommand(Roku.Cmd.VolumeUp);   break;
            case KeyEvent.KEYCODE_VOLUME_DOWN: doCommand(Roku.Cmd.VolumeDown); break;

            default:
                ret = super.onKeyDown(keyCode, evt);
                break;
        }

        return(ret);
    }

    private void parseAndSearch(boolean showToast) {

        String userSearch = ((EditText)findViewById(R.id.searchText)).getText().toString();

        if (lastParseResult != null && userSearch.equals(lastParseInput)) {
            resolveAndSendSearch(lastParseResult, showToast);
            return;
        }

        String parseInput = (overrideParseInput == null ? userSearch : overrideParseInput);
        overrideParseInput = null;

        String parseUrlFormat =
                getApplicationContext().getResources().getString(R.string.parse_url_fmt);

        // you might think this should be parseInput, but it's not ... in the case of
        // receiving input from the share panel we may be showing something very different
        // to the user than we actually parse, and this user-visible string is what we
        // compare against to see if we need to parse again. Sorry about all this.
        lastParseInput = userSearch;

        SearchParser.parse(parseUrlFormat, parseInput, getChannelsCSV(),
                new SearchParser.ParseResultHandler() {

            @Override public void success(SearchParser.ParsedResult result) {
                lastParseResult = result;
                resolveAndSendSearch(result, showToast);
            }
            @Override public void error(Exception e) {
                String msg = getString(R.string.send_failure_parse) + " (" + e.toString() + ")";
                log.e(msg); // toasty(msg);

                SearchParser.ParsedResult errResult = new SearchParser.ParsedResult();
                errResult.Search = userSearch;
                resolveAndSendSearch(errResult, showToast);
            }
        });
    }

    private void resolveAndSendSearch(SearchParser.ParsedResult parseResult, boolean showToast) {

        final Roku.RokuSearchParams params = new Roku.RokuSearchParams();
        params.Search = parseResult.Search;
        params.Season = parseResult.Season;

        if (parseResult.Channels == null || parseResult.Channels.size() == 0) {
            // no channel
            sendSearch(params, showToast);
        }
        else if (parseResult.Channels.size() == 1) {
            // exactly one channel
            params.Channel = parseResult.Channels.get(0).ChannelId;
            params.ContentId = parseResult.Channels.get(0).ContentId;
            params.MediaType = parseResult.Channels.get(0).MediaType;
            sendSearch(params, showToast);
        }
        else {
            // multiple channels --- ask the user to pick
            PopupMenu menu = new PopupMenu(getApplicationContext(),
                    findViewById(R.id.searchButton));

            for (int i = 0; i < parseResult.Channels.size(); ++i) {
                menu.getMenu().add(Menu.NONE, i, Menu.NONE,
                        getChannelName(parseResult.Channels.get(i).ChannelId));
            }

            menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    SearchParser.ParsedResult.ChannelTarget target =
                            parseResult.Channels.get(item.getItemId());

                    params.Channel = target.ChannelId;
                    params.ContentId = target.ContentId;
                    params.MediaType = target.MediaType;

                    sendSearch(params, showToast);
                    return(true);
                }
            });

            menu.show();
        }
    }

    private void sendSearch(Roku.RokuSearchParams params, boolean showToast) {

        Roku.sendSearch(rokuUrl, params, new Roku.CmdResult() {
           @Override public void success() {
               String msg = getString(R.string.send_success);
               log.i("%s", msg); if (showToast) toasty(msg);
           }
           @Override public void error(Exception e) {
               String msg = getString(R.string.send_failure_search) + " (" + e.toString() + ")";
               log.e("%s", msg); // toasty(msg);
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
                log.e("%s", msg); // toasty(msg);
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
                log.e("%s", msg); // toasty(msg);
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
                log.e("%s", msg); // toasty(msg);
            }
        });

    }

    private void populateChannels(boolean triggerSearch) {

        Roku.getChannels(rokuUrl, new Roku.ChannelsHandler() {
            @Override
            public void handle(List<Roku.ChannelInfo> channels) {
                channelList = channels;
                channelAdapter.clear();
                channelAdapter.addAll(channels);
                channelAdapter.notifyDataSetChanged();
                if (triggerSearch) parseAndSearch(true);
            }
            @Override
            public void error(Exception e) {
                String msg = getString(R.string.channels_failure) + " (" + e.toString() + ")";
                log.e("%s", msg); // toasty(msg);
            }
        });

    }

    private String getChannelsCSV() {

        if (channelList == null) return("");

        StringBuilder sb = new StringBuilder();

        for (Roku.ChannelInfo info : channelList) {
            if (sb.length() > 0) sb.append(",");
            sb.append(info.ProviderId);
        }

        return(sb.toString());
    }

    private String getChannelName(String id) {
        for (Roku.ChannelInfo info : channelList) {
            if (info.ProviderId.equals(id)) return(info.Name);
        }

        return(id);
    }

    private void toasty(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    private String overrideParseInput;
    private String lastParseInput;
    private SearchParser.ParsedResult lastParseResult;

    private String rokuName;
    private String rokuUrl;
    private ArrayAdapter channelAdapter;
    private List<Roku.ChannelInfo> channelList;

    private final static Loggy log = new Loggy(ShareToRokuActivity.class.getName());
}