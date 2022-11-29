package com.shutdownhook.sharetoroku.roku;

import com.shutdownhook.sharetoroku.util.Http;
import com.shutdownhook.sharetoroku.util.Loggy;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Roku {

    public interface CmdResult {
        public void success();
        public void error(Exception e);
    }

    // +------------------+
    // | fire and forgets |
    // +------------------+

    public enum Cmd
    {
        Up,
        Down,
        Left,
        Right,
        Back,
        Play,
        Home,
        Select,
        Backspace,
        PowerOn,
        Rev,
        Fwd,
        VolumeUp,
        VolumeDown
    }

    public static void sendCmd(String baseUrl, Cmd cmd, CmdResult handler) {

        http.post(baseUrl + "/keypress/" + cmd.toString(), new Http.StringHandler() {
            @Override public void success(String s) {
                if (handler != null) handler.success();
            }
            @Override public void error(Exception e) {
                if (handler != null) handler.error(e);
            }
        });
    }

    public static void sendString(String baseUrl, String str, CmdResult handler) {

        if (str == null) return;

        // this works because we know that calls will all come back
        // on the ux thread rather than on multiple random threads

        new CmdResult() {

            int ich = 0;

            @Override public void success() {
                if (ich == str.length()) { if (handler != null) handler.success(); }
                else { sendCharacter(baseUrl, str.charAt(ich++), this); }
            }

            @Override public void error(Exception e) {
                if (handler != null) handler.error(e);
            }

            public void start() {
                success();
            }

        }.start();
    }

    public static void sendCharacter(String baseUrl, Character ch, CmdResult handler) {
        String url = baseUrl + "/keypress/Lit_" + Http.urlEncode(ch.toString());
        http.post(url, new Http.StringHandler() {
            @Override public void success(String s) { if (handler != null) handler.success(); }
            @Override public void error(Exception e) { if (handler != null) handler.error(e); }
        });
    }

    // +-------------+
    // | getChannels |
    // | openChannel |
    // +-------------+

    public static class ChannelInfo
    {
        public String Name;
        public String ProviderId;

        @Override
        public String toString() { return(Name); }
    }

    public interface ChannelsHandler {
        public void handle(List<ChannelInfo> channels);
        public void error(Exception e);
    }

    public static void getChannels(String baseUrl, ChannelsHandler handler) {

        http.get(baseUrl + "/query/apps", new Http.XmlHandler() {
            @Override
            public void success(Document response) {

                List<ChannelInfo> channels = new ArrayList<ChannelInfo>();
                NodeList apps = response.getElementsByTagName("app");
                for (int i = 0; i < apps.getLength(); ++i) {
                    ChannelInfo info = new ChannelInfo();
                    channels.add(info);

                    Node node = apps.item(i);
                    info.Name = node.getTextContent().trim();
                    info.ProviderId = node.getAttributes().getNamedItem("id")
                            .getTextContent().trim();
                }

                channels.sort(new Comparator<ChannelInfo>() {
                   public int compare(ChannelInfo info1, ChannelInfo info2) {
                       if (info1 == info2) return(0);
                       if (info1 == null) return(-1);
                       if (info2 == null) return(1);

                       int cmp = info1.Name.compareToIgnoreCase(info2.Name);
                       if (cmp == 0) cmp = info1.ProviderId.compareToIgnoreCase(info2.ProviderId);
                       return(cmp);
                   }
                });

                handler.handle(channels);
            }
            @Override
            public void error(Exception e) {
                handler.error(e);
            }
        });
    }

    public static void openChannel(String baseUrl, String providerId, CmdResult handler) {
        http.post(baseUrl + "/launch/" + providerId, new Http.StringHandler() {
            @Override public void success(String s) { handler.success(); }
            @Override public void error(Exception e) { handler.error(e); }
        });
    }

    // +------------+
    // | sendSearch |
    // +------------+

    public static class RokuSearchParams
    {
        public String Search;
        public String Season;
        public String Channel;
        public String ContentId;
        public String MediaType;
    }

    public static void sendSearch(String baseUrl,
                                  RokuSearchParams params,
                                  CmdResult handler) {

        if (params.Channel != null && params.ContentId != null) {

            sendDeepLink(baseUrl,
                    params.Channel,
                    params.ContentId,
                    params.MediaType,
                    handler);

            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(baseUrl).append("search/browse");

        String search = (params.Search == null ? "" : Http.urlEncode(params.Search));

        sb.append("?keyword=").append(search);

        if (params.Channel != null || params.Season != null) {
            sb.append("&launch=true&title=").append(search);
        }

        if (params.Channel != null) {
            sb.append("&match-any=true&provider-id=").append(Http.urlEncode(params.Channel));
        }

        if (params.Season != null) {
            sb.append("&type=tv-show&season=").append(Http.urlEncode(params.Season));
        }

        String searchUrl = sb.toString();
        log.i("Search URL = %s", searchUrl);

        http.post(searchUrl, new Http.StringHandler() {
            @Override public void success(String s) { handler.success(); }
            @Override public void error(Exception e) { handler.error(e); }
        });
    }

    // +--------------+
    // | sendDeepLink |
    // +--------------+

    public static void sendDeepLink(String baseUrl, String channel,
                                    String contentId, String mediaType,
                                    CmdResult handler) {

        StringBuilder sb = new StringBuilder();

        sb.append(baseUrl)
                .append("launch/")
                .append(Http.urlEncode(channel))
                .append("?contentId=")
                .append(Http.urlEncode(contentId));

        if (mediaType != null) {
            sb.append("&mediaType=").append(Http.urlEncode(mediaType));
        }

        String url = sb.toString();
        log.i("DeepLink URL = %s", url);

        http.post(url, new Http.StringHandler() {
            @Override public void success(String s) { handler.success(); }
            @Override public void error(Exception e) { handler.error(e); }
        });

    }

    // +----------------+
    // | getRokus       |
    // | getCachedRokus |
    // +----------------+

    private final static String ROKU_ST = "roku:ecp";
    private final static int TIMEOUT_SECONDS = 4;
    private final static int NAME_FETCH_BUFFER_MILLIS = 750;

    public static class RokuInfo {
        public String Url;
        public String Name;

        @Override
        public String toString() { return(Name == null ? Url : Name); }

        @Override
        public boolean equals(Object o) {
            if (o == this) return(true);
            if (!(o instanceof RokuInfo)) return(false);
            RokuInfo otherRoku = (RokuInfo) o;
            return(otherRoku.Url.equals(this.Url) && otherRoku.Name.equals(this.Name));
        }
    }

    public static Set<RokuInfo> getCachedRokus() {
        synchronized(listLock) { return(rokus); }
    }

    public static Set<RokuInfo> getRokus() {
        Set<RokuInfo> newRokus = fetchRokus();
        synchronized(listLock) { rokus = newRokus; lastFetch = Instant.now(); }
        return(newRokus);
    }

    private static Set<RokuInfo> fetchRokus() {

        final Set<RokuInfo> newRokus = new HashSet<RokuInfo>();
        final Map<String,String> names = new HashMap<String,String>();

        try {
            log.i("Sending roku ssdp probe");
            Ssdp.probe(ROKU_ST, TIMEOUT_SECONDS, new Ssdp.Callback() {
                @Override
                public void handle(Map<String, String> msg) {
                    RokuInfo roku = new RokuInfo();
                    roku.Url = msg.get("location").trim();
                    roku.Name = msg.get("usn").trim();
                    lookupRokuName(roku.Url, names);
                    newRokus.add(roku);
                    log.i("Found roku: %s", roku.toString());
                }
            });

            Thread.sleep(NAME_FETCH_BUFFER_MILLIS); // allow last name request to come back

            for (Roku.RokuInfo roku : newRokus) {
                if (names.containsKey(roku.Url)) roku.Name = names.get(roku.Url);
            }

        }
        catch (IOException e) {
            log.e("exception fetching rokus (%s)", e.toString());
        }
        catch (InterruptedException eInt) {
            // just get out of dodge
        }

        return(newRokus);
    }

    private static void lookupRokuName(String url, final Map<String,String> names) {
        http.get(url, new Http.XmlHandler() {
            public void success(Document doc) {
                NodeList friendlyNames = doc.getElementsByTagName("friendlyName");
                if (friendlyNames.getLength() == 0) {
                    log.e("No friendly name tag for: %s", url);
                }
                else {
                    String name = friendlyNames.item(0).getTextContent().trim();
                    names.put(url, name);
                }
            }
            public void error(Exception e) {
                log.e("Exception fetching roku name: %s", e.toString());
            }
        });
    }

    // +-------------------+
    // | Helpers & Members |
    // +-------------------+

    private static Http http = new Http();

    private static Object listLock = new Object();
    private static Set<RokuInfo> rokus = null;
    private static Instant lastFetch = Instant.MIN;

    private final static Loggy log = new Loggy(Roku.class.getName());
}


