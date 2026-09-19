package ng.stapletrack.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import ng.stapletrack.entity.Zone;

/**
 * Colours the Nigeria states map (static/img/nigeria-zones.svg, MapSVG, CC BY 4.0) by zone for inline use.
 *
 * <p>Each state is an element with {@code class="ng-state"} and a {@code data-zone} holding a {@link Zone}
 * name. States in a zone with a colour get that fill; the rest get a neutral cream fill. Every state also
 * gets a {@code <title>} tooltip such as "Lagos — South West · ₦2,146".
 */
@Component
public class NigeriaMapRenderer {

	static final String MAP_RESOURCE = "static/img/nigeria-zones.svg";
	static final String NO_DATA_FILL = "var(--st-cream-100)";
	private static final String SVG_NS = "http://www.w3.org/2000/svg";
	private static final String STATE_CLASS = "ng-state";

	/** The map as shipped, minus its metadata block; read once and reused for every request. */
	private final String baseSvg;

	public NigeriaMapRenderer() {
		this(loadClasspathSvg());
	}

	NigeriaMapRenderer(String svg) {
		this.baseSvg = serialize(parse(svg));
	}

	/** The map coloured by zone, with state tooltips showing each zone's price. */
	public String render(Map<Zone, String> colorsByZone, Map<Zone, BigDecimal> pricesByZone) {
		return colorize(baseSvg, colorsByZone, pricesByZone);
	}

	/** {@link #colorize(String, Map, Map)} without prices; tooltips then read "State — Zone". */
	public static String colorize(String svg, Map<Zone, String> colorsByZone) {
		return colorize(svg, colorsByZone, Map.of());
	}

	/**
	 * Returns {@code svg} with a fill style and a {@code <title>} on every {@code ng-state} element.
	 * Elements whose zone has no colour (or an unknown zone) get {@link #NO_DATA_FILL}. The result is the
	 * {@code <svg>} element alone — no XML declaration, comments or {@code <metadata>} — ready to inline.
	 */
	public static String colorize(String svg, Map<Zone, String> colorsByZone, Map<Zone, BigDecimal> pricesByZone) {
		Document doc = parse(svg);
		NodeList all = doc.getElementsByTagNameNS("*", "*");
		for (int i = 0; i < all.getLength(); i++) {
			Element el = (Element) all.item(i);
			if (!hasClass(el, STATE_CLASS)) {
				continue;
			}
			Optional<Zone> zone = zoneOf(el.getAttribute("data-zone"));
			String fill = zone.map(colorsByZone::get).orElse(null);
			el.setAttribute("style", "fill: " + (fill != null ? fill : NO_DATA_FILL) + "; stroke: #FFFFFF; stroke-width: 0.5;");

			removeTitles(el);
			Element title = doc.createElementNS(SVG_NS, "title");
			title.setTextContent(tooltip(el.getAttribute("data-state-name"), zone.orElse(null),
					zone.map(pricesByZone::get).orElse(null)));
			el.insertBefore(title, el.getFirstChild());
		}
		return serialize(doc);
	}

	static String tooltip(String stateName, Zone zone, BigDecimal price) {
		String name = stateName.isBlank() ? "Unknown state" : stateName;
		if (zone == null) {
			return name;
		}
		String text = name + " — " + Zones.displayName(zone);
		return price == null ? text : text + " · ₦" + wholeNaira(price);
	}

	private static String wholeNaira(BigDecimal price) {
		DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		return format.format(price.setScale(0, RoundingMode.HALF_UP));
	}

	private static Optional<Zone> zoneOf(String value) {
		try {
			return value.isBlank() ? Optional.empty() : Optional.of(Zone.valueOf(value.trim()));
		}
		catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static boolean hasClass(Element el, String className) {
		for (String c : el.getAttribute("class").split("\\s+")) {
			if (c.equals(className)) {
				return true;
			}
		}
		return false;
	}

	private static void removeTitles(Element el) {
		for (Node child = el.getFirstChild(); child != null; ) {
			Node next = child.getNextSibling();
			if (child instanceof Element e && "title".equals(e.getLocalName())) {
				el.removeChild(child);
			}
			child = next;
		}
	}

	private static String loadClasspathSvg() {
		try (InputStream in = new ClassPathResource(MAP_RESOURCE).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException("Could not read " + MAP_RESOURCE, e);
		}
	}

	/** Parses with DTDs and external entities disabled — the SVG is inlined into every page. */
	private static Document parse(String svg) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(new StringReader(svg)));
			stripMetadata(doc);
			return doc;
		}
		catch (ParserConfigurationException | SAXException | IOException e) {
			throw new IllegalArgumentException("Not a well-formed SVG document", e);
		}
	}

	/** Drops {@code <metadata>} (the shipped file carries an ~8 KB provenance manifest) and its namespace. */
	private static void stripMetadata(Document doc) {
		NodeList metadata = doc.getElementsByTagNameNS("*", "metadata");
		for (int i = metadata.getLength() - 1; i >= 0; i--) {
			Node node = metadata.item(i);
			node.getParentNode().removeChild(node);
		}
		doc.getDocumentElement().removeAttribute("xmlns:c2pa");
	}

	private static String serialize(Document doc) {
		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			Transformer transformer = factory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			StringWriter out = new StringWriter();
			// The root element only: leaves out the licence comment that precedes <svg> in the file
			transformer.transform(new DOMSource(doc.getDocumentElement()), new StreamResult(out));
			return out.toString();
		}
		catch (TransformerException e) {
			throw new IllegalStateException("Could not serialise SVG", e);
		}
	}

}
