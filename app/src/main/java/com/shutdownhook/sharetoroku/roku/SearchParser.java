
package com.shutdownhook.sharetoroku.roku;


import com.shutdownhook.sharetoroku.util.Http;
import com.shutdownhook.sharetoroku.util.Loggy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchParser {

    // +--------------+
    // | ParsedResult |
    // +--------------+

    public static class ParsedResult
    {
        public static class ChannelTarget
        {
            public String ChannelId;
            public String ContentId;
            public String MediaType;
        }

        public String Search;
        public String Season;
        public String Number;
        public List<ChannelTarget> Channels = new ArrayList<ChannelTarget>();

        public static ParsedResult fromString(String input) {
            ParsedResult result = new ParsedResult();
            String[] fields = input.split("\\|");
            result.Search = checkNull(fields[0]);
            result.Season = checkNull(fields[1]);
            result.Number = checkNull(fields[2]);

            String channelsString = (fields.length >= 4 ? checkNull(fields[3]) : null);
            if (channelsString != null) {
                for (String channelString : channelsString.split(",")) {
                    String[] channelFields = channelString.split(":");
                    ChannelTarget target = new ChannelTarget();
                    target.ChannelId = checkNull(channelFields[0]);
                    target.ContentId = checkNull(channelFields[1]);
                    target.MediaType = checkNull(channelFields[2]);
                    result.Channels.add(target);
                }
            }

            return(result);
        }

        private static String checkNull(String input) {
            return((input != null && !input.toLowerCase().equals("null")) ? input : null);
        }
    }

    // +-------+
    // | parse |
    // +-------+

    public interface ParseResultHandler {
        public void success(ParsedResult result);
        public void error(Exception e);
    }

    public static void parse(String urlFormat, String input, String channelsCSV,
                             ParseResultHandler handler) {

        String url = String.format(urlFormat, Http.urlEncode(input), Http.urlEncode(channelsCSV));

        http.get(url, new Http.StringHandler() {
            @Override
            public void success(String response) {
                handler.success(ParsedResult.fromString(response));
            }
            @Override
            public void error(Exception e) {
                handler.error(e);
            }
        });
    }

    // +----------------------+
    // | sanitizeSearchString |
    // +----------------------+

    public static String sanitizeSearchString(String input) {
        log.i("SHARE TEXT: %s", input);
        String clean = input.trim();
        clean = cleanupForChrome(clean);
        clean = cleanupForTvTime(clean);
        return(clean);
    }


    private static String cleanupForChrome(String input) {

        // chrome puts selected text in quotes and then follows it with the url.
        // others like the Netflix app do a similar thing that we try to catch
        // with this pattern
        Matcher m = CHROME_STYLE_REGEX.matcher(input.replaceAll("\n", " "));
        return(m.matches() ? m.group(1) : input);
    }

    private static String cleanupForTvTime(String input) {

        // look for text before " on TV Time" --- only useful sharing from
        // the show page though. this is all just the worst.
        int ichTarget = input.indexOf(" on TV Time");
        return(ichTarget == -1 ? input : input.substring(0, ichTarget));
    }

    // +-------------------+
    // | Helpers & Members |
    // +-------------------+

    private static Http http = new Http();

    private static Pattern CHROME_STYLE_REGEX =
            Pattern.compile("[^\\\"]*\\\"([^\\\"]+)\\\".+[hH][tT][tT][pP].+");


    private final static Loggy log = new Loggy(SearchParser.class.getName());
}
