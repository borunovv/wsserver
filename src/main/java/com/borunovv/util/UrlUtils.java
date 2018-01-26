package com.borunovv.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author borunovv
 */
public final class UrlUtils {

    // http://aaa.bbb/ccc/ddd?a=1&b=2 -> '/ccc/ddd'
    public static String getUrlPath(String url) {
        url = url.trim();
        int offset = url.indexOf("://");
        if (offset >= 0) {
            offset += 3;
        } else {
            offset = 0;
        }

        if (url.indexOf('/', offset) >= 0) {
            offset = url.indexOf('/', offset);
        }
        String path = url.isEmpty() ?
                "" :
                url.substring(offset);

        if (path.contains("?")) {
            path = path.substring(0, path.indexOf('?'));
        }

        // collapse '/./' -> '/'
        while (path.contains("/./")) {
            path = path.replaceAll(Pattern.quote("/./"), "/");
        }

        return path;
    }

    // http://aaa.bbb/ccc/ddd?a=1&b=2 -> 'a=1&b=2'
    public static String getUriParamsPart(String url) {
        return (url.contains("?") && url.lastIndexOf('?') < url.length() - 1) ?
                url.substring(url.lastIndexOf('?') + 1) :
                "";
    }

    // a=1&b=2 -> [a:1, b:2]
    public static Map<String, String> parseUriParams(String paramString) {
        Map<String, String> res = new HashMap<String, String>();
        String[] rowParams = paramString.split("&");
        for (String rowParam : rowParams) {
            String[] params = rowParam.split("=");
            if (params.length == 2) {
                res.put(urlDecode(params[0]), urlDecode(params[1]));
            }
        }
        return res;
    }

    public static String urlEncode(String string) {
        String res = string;
        try {
            res = URLEncoder.encode(StringUtils.ensureString(string), "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        return res;
    }

    public static String urlDecode(String string) {
        String res = string;
        try {
            res = URLDecoder.decode(StringUtils.ensureString(string), "UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        return res;
    }
}
