package searchengine.services;

import searchengine.config.SiteConfig;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {
    private static Pattern pattern = Pattern.compile("((http[s]?|ftp)://)([^:^/]*)(:(\\d*))?(.*)?");
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");
    private static String[] inCorrectExtension = {"webp", "jpg", "jpeg", "pdf", "png"};

    public static String getDomainName(String url) {

        Matcher matcher = pattern.matcher(url.toLowerCase());

        if (!matcher.find()) {
            return null;
        }
        return matcher.group(3).replace("www.", "");
    }

    public static String getPath(String url, String domain) {
        return url.substring(url.indexOf(domain) + domain.length()).toLowerCase();
    }

    public static Site iniSite(SiteConfig siteConfig) {
        Site site = new Site();
        site.setName(siteConfig.getName());
        site.setUrl(siteConfig.getUrl());
        site.setDomain(getDomainName(siteConfig.getUrl()));
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());

        return site;
    }

    public static Page iniPage(Site site, String url) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(getPath(url, getDomainName(url)));
        page.setUrl(url);
        return page;
    }

    public static void print(String text) {
        System.out.println(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()) + ":: " + text);
    }

    public static String extractWord(String text) {
        return text.replaceAll("<.*?>", " ")
                .replaceAll("body\\{(.+?)}", " ")
                .replaceAll("&.*?;", "")
                .replaceAll("[^ а-яА-Я\\.]", " ")
                .replaceAll("\\s+", " ")
                ;
    }

    public static boolean incorrectUrl(String url) {
        if (url.contains("#")) {
            return true;
        }
        for (String ext : inCorrectExtension) {
            if (url.matches(".*\\." + ext)) {
                return true;
            }
        }
        return false;
    }

    public static String getTimeStr(LocalDateTime date) {
        return date.format(formatter);
    }

}
