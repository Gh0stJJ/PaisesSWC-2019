package org.example;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;

import java.io.*;
import java.util.*;

public class ResumenISWC2019 {
    // Define namespaces
    private static final String CON_NS = "https://w3id.org/scholarlydata/ontology/conference-ontology.owl#";
    private static final String PURL_NS = "http://purl.org/dc/elements/1.1/";
    private static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
    private static final String DBO_NS = "http://dbpedia.org/ontology/";
    private static final String DBP_NS = "http://dbpedia.org/property/";

    private Model model;
    private Property hasAuthorList;
    private Property hasFirstItem;
    private Property hasContent;
    private Property next;

    public ResumenISWC2019(String directory) {
        this.model = ModelFactory.createDefaultModel();
        this.next = model.createProperty(CON_NS + "next");
        this.hasContent = model.createProperty(CON_NS + "hasContent");
        this.hasFirstItem = model.createProperty(CON_NS + "hasFirstItem");
        this.hasAuthorList = model.createProperty(CON_NS + "hasAuthorList");
        loadModels(directory);
    }

    private void loadModels(String directory) {
        File dir = new File(directory);
        if (!dir.isDirectory()){
            throw new IllegalArgumentException("Provided path is not a directory: " + directory);
        }
        for (File file: Objects.requireNonNull(dir.listFiles())){
            if (file.getName().endsWith(".ttl")) {
                try(InputStream in = new FileInputStream(file)){
                    model.read(in, null, "TURTLE");
                } catch (IOException e) {
                    System.err.println("Error reading file: " + file.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    private String getAuthorList(Resource article){
        Statement listStmt = article.getProperty(hasAuthorList);
        if (listStmt == null) return "";
        Resource node = listStmt.getResource();
        Statement firstStmt = node.getProperty(hasFirstItem);
        if(firstStmt == null) return "";

        List<String> authors = new ArrayList<>();
        Resource item = firstStmt.getResource();
        while (item != null){
            Statement contentStmt = item.getProperty(hasContent);
            if (contentStmt != null){
                Resource authRes = contentStmt.getResource();
                Statement labelStmt = authRes.getProperty(model.createProperty(RDFS_NS+ "label"));
                if(labelStmt != null){
                    authors.add(labelStmt.getString());
                }
            }
            Statement nextStmt = item.getProperty(next);
            item = (nextStmt != null) ? nextStmt.getResource() : null;
        }
        if (authors.isEmpty()) return "";
        if (authors.size() == 1) return authors.getFirst(); //cambio
        return String.join(", ", authors.subList(0, authors.size() - 1)) + " y " + authors.get(authors.size() -1);

    }

    private String mapTrack(String track){
        return switch (track) {
            case "Research" -> "(IN)";
            case "In-Use" -> "(EU)";
            default -> "(RC)";
        };
    }

    public Map<String, Set<String>> getData() {
        Map<String, Set<String>> data = new TreeMap<>();
        String prefixes = String.join("\n",
                "PREFIX conference: <" + CON_NS + ">",
                "PREFIX purl:       <" + PURL_NS + ">",
                "PREFIX rdfs:       <" + RDFS_NS + ">",
                "PREFIX dbo:        <" + DBO_NS + ">",
                "PREFIX dbp:        <" + DBP_NS + ">"
        );
        String queryString = prefixes + "\n" +
                "SELECT DISTINCT ?pais ?articulo ?iriarticulo ?track WHERE {\n" +
                "  ?t a conference:Track ;\n" +
                "     conference:hasSubEvent ?e ;\n" +
                "     rdfs:label ?track .\n" +
                "  ?e a conference:Talk ;\n" +
                "     conference:isEventRelatedTo ?iriarticulo .\n" +
                "  ?iriarticulo rdfs:label ?articulo ;\n" +
                "              purl:creator/conference:hasAffiliation/\n" +
                "              conference:withOrganisation/dbo:country/dbp:name ?pais .\n" +
                "  FILTER(?track IN (\"Research\", \"In-Use\", \"Resource\"))\n" +
                "}";


        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                String country = sol.getLiteral("pais").getString();
                String title   = sol.getLiteral("articulo").getString();
                String track   = sol.getLiteral("track").getString();
                Resource artRes = sol.getResource("iriarticulo");
                String entry   = mapTrack(track) + " \"" + title + "\" por " + getAuthorList(artRes);
                data.computeIfAbsent(country, k -> new TreeSet<>()).add(entry);
            }
        }
        return data;
    }

    public void generateHTML(Map<String, Set<String>> data, String outputFile) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("<title>Publicaciones ISWC 2019</title>\n");
        // Cortecia de Tailwind CSS para estilos
        html.append("<link href=\"https://cdn.jsdelivr.net/npm/tailwindcss@2.2.19/dist/tailwind.min.css\" rel=\"stylesheet\">\n");
        html.append("</head>\n<body class=\"bg-gray-100 text-gray-900\">\n");
        html.append("<header class=\"bg-blue-600 py-6 mb-8\">\n");
        html.append("  <h1 class=\"text-center text-white text-4xl font-extrabold\">Publicaciones ISWC 2019</h1>\n");
        html.append("</header>\n");
        html.append("<div class=\"container mx-auto px-4\">\n");
        html.append("  <p class=\"text-lg mb-6\">ISWC es el principal foro internacional para la comunidad de datos enlazados y web semántica. Esta página enumera las publicaciones de los tópicos Research, In-Use y Resource, agrupadas por país.</p>\n");
        for (String country : data.keySet()) {
            html.append("  <section class=\"bg-white rounded-lg shadow-md p-6 mb-6\">\n");
            html.append("    <h2 class=\"text-2xl font-semibold text-blue-700 mb-4\">" + country + "</h2>\n");
            html.append("    <ul class=\"list-disc list-inside space-y-2\">\n");
            for (String entry : data.get(country)) {
                html.append("      <li class=\"hover:underline hover:text-blue-600 transition-colors\">" + entry + "</li>\n");
            }
            html.append("    </ul>\n  </section>\n");
        }
        html.append("</div>\n</body>\n</html>");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(html.toString());
            System.out.println("HTML generado en: " + outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        String filePath = "src/main/resources/";
        ResumenISWC2019 resumen = new ResumenISWC2019(filePath);
        Map<String, Set<String>> data = resumen.getData();
        resumen.generateHTML(data, "PublicacionesISWC2019.html");
    }
}
