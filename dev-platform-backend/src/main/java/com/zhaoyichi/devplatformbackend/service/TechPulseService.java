package com.zhaoyichi.devplatformbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoyichi.devplatformbackend.vo.TechPulseItemVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 聚合公开技术资讯（RSS / Atom + Dev.to 官方 API），按分类缓存；支持 refresh 跳过缓存。
 * 标签展示为「#话题」或站点名，不出现冗余的 DEV 前缀。
 */
@Service
public class TechPulseService {
    private static final Logger log = LoggerFactory.getLogger(TechPulseService.class);
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    private static final Set<String> KNOWN_CATEGORIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "all", "tech", "opensource", "law", "github", "blockchain", "devops", "security", "ai"
    )));

    private final RestTemplate http;
    private final ObjectMapper objectMapper;
    private final Object fetchLock = new Object();
    private final ConcurrentHashMap<String, CacheSlot> cacheByCategory = new ConcurrentHashMap<>();

    public TechPulseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(12000);
        this.http = new RestTemplate(factory);
    }

    public List<TechPulseItemVO> getPulse(int limit, String category, boolean forceRefresh) {
        int cap = Math.max(1, Math.min(limit, 24));
        String cat = normalizeCategory(category);
        long now = System.currentTimeMillis();

        if (!forceRefresh) {
            CacheSlot slot = cacheByCategory.get(cat);
            if (slot != null && now - slot.atMillis < CACHE_TTL_MS && !slot.list.isEmpty()) {
                return limitList(slot.list, cap);
            }
        }

        synchronized (fetchLock) {
            if (!forceRefresh) {
                CacheSlot slot2 = cacheByCategory.get(cat);
                if (slot2 != null && System.currentTimeMillis() - slot2.atMillis < CACHE_TTL_MS && !slot2.list.isEmpty()) {
                    return limitList(slot2.list, cap);
                }
            }
            List<WorkItem> merged = fetchFreshByCategory(cat);
            Map<String, WorkItem> byUrl = new LinkedHashMap<>();
            for (WorkItem w : merged) {
                if (w == null || w.url == null || w.url.isEmpty()) {
                    continue;
                }
                byUrl.putIfAbsent(w.url.trim(), w);
            }
            List<TechPulseItemVO> vos = byUrl.values().stream()
                    .sorted(Comparator.comparingLong((WorkItem w) -> w.epochMillis).reversed())
                    .map(WorkItem::toVo)
                    .collect(Collectors.toList());
            cacheByCategory.put(cat, new CacheSlot(vos, System.currentTimeMillis()));
            return limitList(vos, cap);
        }
    }

    private static List<TechPulseItemVO> limitList(List<TechPulseItemVO> list, int cap) {
        return list.stream().limit(cap).collect(Collectors.toList());
    }

    private static String normalizeCategory(String raw) {
        if (raw == null) {
            return "all";
        }
        String c = raw.trim().toLowerCase(Locale.ROOT);
        if (!KNOWN_CATEGORIES.contains(c)) {
            return "all";
        }
        return c;
    }

    private List<WorkItem> fetchFreshByCategory(String cat) {
        List<WorkItem> all = new ArrayList<>();
        switch (cat) {
            case "all":
                all.addAll(safeList(() -> fetchDevTo("github", "#github")));
                all.addAll(safeList(() -> fetchDevTo("opensource", "#opensource")));
                all.addAll(safeList(() -> fetchFeed("https://github.blog/feed/", "GitHub Blog")));
                all.addAll(safeList(() -> fetchFeed("https://stackoverflow.blog/feed/", "Stack Overflow Blog")));
                break;
            case "tech":
                all.addAll(safeList(() -> fetchDevTo("programming", "#programming")));
                all.addAll(safeList(() -> fetchDevTo("webdev", "#webdev")));
                all.addAll(safeList(() -> fetchDevTo("discuss", "#discuss")));
                all.addAll(safeList(() -> fetchFeed("https://stackoverflow.blog/feed/", "Stack Overflow Blog")));
                break;
            case "opensource":
                all.addAll(safeList(() -> fetchDevTo("opensource", "#opensource")));
                all.addAll(safeList(() -> fetchDevTo("beginners", "#beginners")));
                all.addAll(safeList(() -> fetchFeed("https://opensource.com/feed", "Open Source")));
                break;
            case "law":
                all.addAll(safeList(() -> fetchDevTo("legal", "#legal")));
                all.addAll(safeList(() -> fetchDevTo("privacy", "#privacy")));
                all.addAll(safeList(() -> fetchDevTo("gdpr", "#gdpr")));
                all.addAll(safeList(() -> fetchFeed("https://www.eff.org/rss/updates.xml", "EFF")));
                break;
            case "github":
                all.addAll(safeList(() -> fetchDevTo("github", "#github")));
                all.addAll(safeList(() -> fetchDevTo("git", "#git")));
                all.addAll(safeList(() -> fetchFeed("https://github.blog/feed/", "GitHub Blog")));
                break;
            case "blockchain":
                all.addAll(safeList(() -> fetchDevTo("blockchain", "#blockchain")));
                all.addAll(safeList(() -> fetchDevTo("ethereum", "#ethereum")));
                break;
            case "devops":
                all.addAll(safeList(() -> fetchDevTo("devops", "#devops")));
                all.addAll(safeList(() -> fetchDevTo("kubernetes", "#kubernetes")));
                all.addAll(safeList(() -> fetchDevTo("docker", "#docker")));
                break;
            case "security":
                all.addAll(safeList(() -> fetchDevTo("security", "#security")));
                all.addAll(safeList(() -> fetchDevTo("infosec", "#infosec")));
                break;
            case "ai":
                all.addAll(safeList(() -> fetchDevTo("ai", "#ai")));
                all.addAll(safeList(() -> fetchDevTo("machinelearning", "#machinelearning")));
                break;
            default:
                all.addAll(safeList(() -> fetchDevTo("github", "#github")));
                break;
        }
        return all;
    }

    private List<WorkItem> safeList(Fetcher f) {
        try {
            List<WorkItem> list = f.fetch();
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            log.warn("tech pulse source skipped: {}", e.toString());
            return Collections.emptyList();
        }
    }

    @FunctionalInterface
    private interface Fetcher {
        List<WorkItem> fetch() throws Exception;
    }

    private List<WorkItem> fetchDevTo(String tag, String sourceLabel) throws Exception {
        String url = "https://dev.to/api/articles?per_page=8&tag=" + tag;
        String body = httpGetXmlOrJson(url);
        List<Map<String, Object>> arr = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() {
        });
        List<WorkItem> out = new ArrayList<>();
        for (Map<String, Object> row : arr) {
            if (row == null) {
                continue;
            }
            String title = Objects.toString(row.get("title"), "").trim();
            String link = Objects.toString(row.get("url"), "").trim();
            if (title.isEmpty() || link.isEmpty()) {
                continue;
            }
            String desc = Objects.toString(row.get("description"), "");
            String published = Objects.toString(row.get("published_at"), "");
            long epoch = parseIsoInstant(published);
            String when = formatDisplay(epoch, published);
            out.add(new WorkItem(title, link, sourceLabel, textPreview(desc, 220), when, epoch));
        }
        return out;
    }

    private List<WorkItem> fetchFeed(@NonNull String feedUrl, String sourceLabel) throws Exception {
        String xml = httpGetXmlOrJson(feedUrl);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList rssItems = doc.getElementsByTagName("item");
        if (rssItems.getLength() > 0) {
            return parseRssItems(rssItems, sourceLabel);
        }
        NodeList atomEntries = doc.getElementsByTagNameNS(ATOM_NS, "entry");
        if (atomEntries.getLength() > 0) {
            return parseAtomEntries(atomEntries, sourceLabel);
        }
        return Collections.emptyList();
    }

    private List<WorkItem> parseRssItems(NodeList items, String sourceLabel) {
        List<WorkItem> out = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Node n = items.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element el = (Element) n;
            String title = childText(el, "title").trim();
            String link = childText(el, "link").trim();
            if (link.isEmpty()) {
                link = childText(el, "guid").trim();
            }
            if (title.isEmpty() || link.isEmpty()) {
                continue;
            }
            String pub = childText(el, "pubDate").trim();
            long epoch = parseRfc1123(pub);
            String desc = firstNonEmpty(
                    childText(el, "description"),
                    childTextsByLocalName(el, "encoded")
            );
            String when = formatDisplay(epoch, pub);
            out.add(new WorkItem(title, link, sourceLabel, textPreview(desc, 220), when, epoch));
        }
        return out;
    }

    private List<WorkItem> parseAtomEntries(NodeList entries, String sourceLabel) {
        List<WorkItem> out = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Node n = entries.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element el = (Element) n;
            String title = childTextNs(el, ATOM_NS, "title").trim();
            String link = atomAlternateLink(el);
            if (title.isEmpty() || link.isEmpty()) {
                continue;
            }
            String updated = childTextNs(el, ATOM_NS, "updated").trim();
            String published = childTextNs(el, ATOM_NS, "published").trim();
            String pub = !published.isEmpty() ? published : updated;
            long epoch = parseIsoInstant(pub);
            String summary = firstNonEmpty(
                    childTextNs(el, ATOM_NS, "summary"),
                    childTextNs(el, ATOM_NS, "content")
            );
            String when = formatDisplay(epoch, pub);
            out.add(new WorkItem(title, link, sourceLabel, textPreview(summary, 220), when, epoch));
        }
        return out;
    }

    private static String atomAlternateLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS(ATOM_NS, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Node n = links.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            Element le = (Element) n;
            String rel = le.getAttribute("rel");
            if (rel != null && !rel.isEmpty() && !"alternate".equals(rel)) {
                continue;
            }
            String href = le.getAttribute("href");
            if (href != null && !href.trim().isEmpty()) {
                return href.trim();
            }
        }
        return "";
    }

    private static String childTextNs(Element parent, String ns, String local) {
        NodeList nl = parent.getElementsByTagNameNS(ns, local);
        if (nl.getLength() == 0) {
            return "";
        }
        Node n = nl.item(0);
        return n == null || n.getTextContent() == null ? "" : n.getTextContent();
    }

    private static String childTextsByLocalName(Element parent, String localEndsWith) {
        NodeList all = parent.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) {
                continue;
            }
            String name = ((Element) n).getLocalName();
            if (name != null && name.endsWith(localEndsWith)) {
                String t = n.getTextContent();
                if (t != null && !t.trim().isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) {
            return a;
        }
        return b == null ? "" : b;
    }

    private String httpGetXmlOrJson(@NonNull String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "DevPlatformHomeBot/1.0 (+student project)");
        headers.set(HttpHeaders.ACCEPT, "application/json, application/rss+xml, application/xml, text/xml, */*");
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> resp = http.exchange(Objects.requireNonNull(url, "url"), HttpMethod.GET, entity, String.class);
        return resp.getBody() == null ? "" : resp.getBody();
    }

    private static String childText(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) {
            return "";
        }
        Node n = nl.item(0);
        return n == null ? "" : n.getTextContent() == null ? "" : n.getTextContent();
    }

    private static long parseIsoInstant(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0L;
        }
        String s = iso.trim();
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return ZonedDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static long parseRfc1123(String pubDate) {
        if (pubDate == null || pubDate.isEmpty()) {
            return 0L;
        }
        try {
            return ZonedDateTime.parse(pubDate.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e1) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US);
                return ZonedDateTime.parse(pubDate.trim(), fmt).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                return 0L;
            }
        }
    }

    private static String formatDisplay(long epochMillis, String fallback) {
        if (epochMillis > 0) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(epochMillis));
        }
        if (fallback != null && fallback.length() >= 16) {
            return fallback.length() > 19 ? fallback.substring(0, 19) : fallback;
        }
        return "";
    }

    private static String textPreview(String raw, int max) {
        if (raw == null) {
            return "";
        }
        String t = raw.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (t.length() > max) {
            return t.substring(0, max) + "…";
        }
        return t;
    }

    private static final class CacheSlot {
        final List<TechPulseItemVO> list;
        final long atMillis;

        CacheSlot(List<TechPulseItemVO> list, long atMillis) {
            this.list = list;
            this.atMillis = atMillis;
        }
    }

    private static final class WorkItem {
        final String title;
        final String url;
        final String source;
        final String summary;
        final String publishedAt;
        final long epochMillis;

        WorkItem(String title, String url, String source, String summary, String publishedAt, long epochMillis) {
            this.title = title;
            this.url = url;
            this.source = source;
            this.summary = summary;
            this.publishedAt = publishedAt;
            this.epochMillis = epochMillis;
        }

        TechPulseItemVO toVo() {
            TechPulseItemVO vo = new TechPulseItemVO();
            vo.setTitle(title);
            vo.setUrl(url);
            vo.setSource(source);
            vo.setSummary(summary);
            vo.setPublishedAt(publishedAt);
            return vo;
        }
    }
}
