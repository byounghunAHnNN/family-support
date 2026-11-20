package com.familysupport.app.collect.infrastructure;

import com.familysupport.app.collect.domain.FeedException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** 복지로 XML 응답 → 항목 목록. 외부 문서이므로 DTD·외부 엔티티를 막고 읽는다. */
final class XmlItems {

    /** @param totalCount 전체 건수(페이지 반복 판단용) */
    record Page(int totalCount, List<Map<String, String>> items) {
    }

    private XmlItems() {
    }

    /**
     * @param itemTag 항목 태그(복지로 = servList)
     * @throws FeedException XML이 아니거나 resultCode가 0이 아님(인증 오류 등)
     */
    static Page parse(String body, String itemTag) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
        } catch (Exception e) {
            throw new FeedException("XML 응답을 읽지 못했습니다: " + abbreviate(body));
        }
        String code = text(doc, "resultCode");
        if (!"0".equals(code) && !"00".equals(code)) {
            String reason = firstNonBlank(text(doc, "resultMessage"), text(doc, "returnAuthMsg"), text(doc, "errMsg"));
            throw new FeedException("응답 오류(resultCode=" + code + "): " + reason);
        }
        List<Map<String, String>> items = new ArrayList<>();
        NodeList nodes = doc.getElementsByTagName(itemTag);
        for (int i = 0; i < nodes.getLength(); i++) {
            Map<String, String> item = new LinkedHashMap<>();
            NodeList children = nodes.item(i).getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child instanceof Element element) {
                    item.put(element.getTagName(), element.getTextContent().trim());
                }
            }
            items.add(item);
        }
        String total = text(doc, "totalCount");
        return new Page(total == null || total.isBlank() ? items.size() : Integer.parseInt(total.trim()), items);
    }

    /** 항목 1건을 원문 보관용 XML로 되돌린다(태그 순서 유지, 값은 XML 이스케이프). */
    static String toXml(String itemTag, Map<String, String> item) {
        StringBuilder sb = new StringBuilder("<").append(itemTag).append('>');
        item.forEach((k, v) -> sb.append('<').append(k).append('>').append(escape(v)).append("</").append(k).append('>'));
        return sb.append("</").append(itemTag).append('>').toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String text(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String abbreviate(String body) {
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ");
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }
}
