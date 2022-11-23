
package com.shutdownhook.sharetoroku.roku;


import com.shutdownhook.sharetoroku.util.Http;
import com.shutdownhook.sharetoroku.util.Loggy;

public class SearchParser {

    // +--------------+
    // | ParsedResult |
    // +--------------+

    public static class ParsedResult
    {
        public String Search;
        public String Season;
        public String Number;
        public String Channel;
        public String ContentId;
        public String MediaType;

        public static ParsedResult fromString(String input) {
            ParsedResult result = new ParsedResult();
            String[] fields = input.split("\\|");
            result.Search = checkNull(fields[0]);
            result.Season = checkNull(fields[1]);
            result.Number = checkNull(fields[2]);
            result.Channel = checkNull(fields[3]);
            result.ContentId = checkNull(fields[4]);
            result.MediaType = checkNull(fields[5]);
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

        // chrome puts selected text in quotes and then follows it with the url
        if (input.startsWith("\"")) {
            int ichSecondQuote = input.indexOf("\"", 1);
            if (ichSecondQuote != -1) {
                return(input.substring(1, ichSecondQuote));
            }
        }

        return(input);
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

    private final static Loggy log = new Loggy(SearchParser.class.getName());
}
