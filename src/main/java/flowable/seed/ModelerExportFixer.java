package flowable.seed;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Corrige lo que el editor del Modeler cambia al guardar, editando solo esas líneas del XML para que el
 * diff contra el archivo exportado sea mínimo:
 * <ul>
 * <li>Guarda como sequenceFlow las flechas que unen un boundary de compensación con su tarea, y Flowable
 * 6.8 necesita una association ("Parent activity for boundary compensation event could not be found").</li>
 * <li>Le pone al proceso el nombre de la pool ("Institución de capacitación") en vez de "Proyecto Cursos".</li>
 * </ul>
 */
public final class ModelerExportFixer {
    static final String PROCESS_NAME = "Proyecto Cursos";
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final Pattern SUBPROCESS_TAG = Pattern.compile("<(/?)subProcess\\b");
    private static final Pattern PROCESS_NAME_ATTRIBUTE = Pattern.compile("(<process\\s[^>]*?\\bname=\")[^\"]*(\")");

    private ModelerExportFixer() {
    }

    /** Devuelve el XML corregido; si no había nada para corregir, lo devuelve igual. */
    public static String fix(String xml) throws Exception {
        return fixCompensationAssociations(fixProcessName(xml));
    }

    private static String fixProcessName(String xml) {
        return PROCESS_NAME_ATTRIBUTE.matcher(xml).replaceFirst("$1" + PROCESS_NAME + "$2");
    }

    private static String fixCompensationAssociations(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Set<String> compensationBoundaries = new HashSet<>();
        NodeList boundaries = document.getElementsByTagNameNS(BPMN_NS, "boundaryEvent");
        for (int i = 0; i < boundaries.getLength(); i++) {
            Element boundary = (Element) boundaries.item(i);
            if (boundary.getElementsByTagNameNS(BPMN_NS, "compensateEventDefinition").getLength() > 0) {
                compensationBoundaries.add(boundary.getAttribute("id"));
            }
        }

        NodeList flows = document.getElementsByTagNameNS(BPMN_NS, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            if (!compensationBoundaries.contains(flow.getAttribute("sourceRef"))) {
                continue;
            }
            Element container = (Element) flow.getParentNode();
            xml = removeSequenceFlow(xml, flow.getAttribute("id"));
            // Dentro de cada contenedor, el esquema BPMN pide las associations después de los flowElements
            xml = insertBeforeClosingTag(xml, container, String.format(
                    "<association id=\"%s\" sourceRef=\"%s\" targetRef=\"%s\" associationDirection=\"One\"></association>",
                    flow.getAttribute("id"), flow.getAttribute("sourceRef"), flow.getAttribute("targetRef")));
        }
        return xml;
    }

    private static String removeSequenceFlow(String xml, String id) {
        Matcher matcher = Pattern.compile("\\r?\\n[ \\t]*<sequenceFlow\\s[^>]*\\bid=\"" + Pattern.quote(id)
                + "\"[^>]*?(?:/>|>\\s*</sequenceFlow>)").matcher(xml);
        if (!matcher.find()) {
            throw new IllegalStateException("No se encontró el sequenceFlow " + id);
        }
        return xml.substring(0, matcher.start()) + xml.substring(matcher.end());
    }

    private static String insertBeforeClosingTag(String xml, Element container, String element) {
        int closing = closingTagIndex(xml, container);
        int lineStart = xml.lastIndexOf('\n', closing) + 1;
        String indent = xml.substring(lineStart, closing);
        return xml.substring(0, lineStart) + indent + "  " + element + "\n" + xml.substring(lineStart);
    }

    private static int closingTagIndex(String xml, Element container) {
        if ("process".equals(container.getLocalName())) {
            return xml.indexOf("</process>");
        }
        Matcher open = Pattern.compile("<subProcess\\s[^>]*\\bid=\"" + Pattern.quote(container.getAttribute("id")) + "\"")
                .matcher(xml);
        if (!open.find()) {
            throw new IllegalStateException("No se encontró el subProcess " + container.getAttribute("id"));
        }
        // Saltar los subprocesos anidados (por ejemplo, el de Cancelación) hasta el cierre que corresponde
        Matcher tags = SUBPROCESS_TAG.matcher(xml);
        int depth = 0;
        int from = open.start();
        while (tags.find(from)) {
            depth += tags.group(1).isEmpty() ? 1 : -1;
            if (depth == 0) {
                return tags.start();
            }
            from = tags.end();
        }
        throw new IllegalStateException("subProcess sin cierre: " + container.getAttribute("id"));
    }
}
