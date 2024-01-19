import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public final class TransifexFindResources {
  public static void main(String args[]) throws Exception {
    var directories =
      Stream.of(args)
        .map(Paths::get)
        .map(Path::toAbsolutePath)
        .collect(Collectors.toList());

    var resources = new TreeMap<String, String>();
    for (var directory : directories) {
      try (var subFiles = Files.walk(directory)) {
        var subFileList = subFiles.collect(Collectors.toList());

        for (var file : subFileList) {
          if (file.toString().endsWith(".xml")) {
            processFile(file, resources);
          }
        }
      }
    }

    serializeResources(resources);
  }

  private static void serializeResources(TreeMap<String, String> resources) throws Exception {

    var documentBuilders =
      DocumentBuilderFactory.newDefaultInstance();
    var documentBuilder =
      documentBuilders.newDocumentBuilder();
    var document =
      documentBuilder.newDocument();
    var root =
      document.createElement("resources");

    document.appendChild(root);
    for (var entry : resources.entrySet()) {
      var e = document.createElement("string");
      e.setAttribute("name", entry.getKey());
      e.setTextContent(entry.getValue());
      root.appendChild(e);
    }

    try (var output = Files.newOutputStream(Paths.get("Transifex.xml"), CREATE, WRITE, TRUNCATE_EXISTING)) {
      final var transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      final var result = new StreamResult(output);
      final var source = new DOMSource(document);
      transformer.transform(source, result);
      output.flush();
    }
  }

  private static void processFile(Path file, TreeMap<String, String> resources) throws Exception {
    try {
      var documentBuilders =
        DocumentBuilderFactory.newDefaultInstance();
      var documentBuilder =
        documentBuilders.newDocumentBuilder();
      var document =
        documentBuilder.parse(file.toFile());
      var root =
        document.getDocumentElement();

      if (!Objects.equals(root.getTagName(), "resources")) {
        return;
      }

      var stringElements = root.getElementsByTagName("string");
      for (int index = 0; index < stringElements.getLength(); ++index) {
        var element = stringElements.item(index);
        var attributes = element.getAttributes();
        var name = attributes.getNamedItem("name").getTextContent();
        var value = element.getTextContent();
        resources.put(name, value);
      }
    } catch (Exception e) {
      // Don't care
    }
  }
}
