package io.global.geolocation.tools;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.util.RawValue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.System.out;

public class Converter {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String... args) throws IOException {
        out.println("Args: " + Arrays.asList(args));
        out.println("args.length: " + args.length);
        if (args.length < 2) {
            System.err.println("Requires input and output file");
            System.exit(1);
        }
        var inFile = args[0];
        var outFile = args[1];
        var maxStr = args.length > 2?args[2]:null;
        int max = -1;
        if (maxStr != null) {
            max = Integer.parseInt(maxStr);
        }

        int i = 0;
        long start = System.currentTimeMillis();

        try (var parser = mapper.createParser(new File(inFile));
             var writer = mapper.createGenerator(Files.newBufferedWriter(Paths.get(outFile), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            writer.setPrettyPrinter(new DefaultPrettyPrinter());
            while (!parser.isClosed()) {

                JsonToken token = parser.nextToken();
                if (token == JsonToken.START_ARRAY) {
                    writer.writeStartObject();
                    writer.writeStringField("type", "FeatureCollection");
                    /*
                    {
                        "type": "FeatureCollection",
                        "features":
                     */
                    writer.writeArrayFieldStart("features");
                } else if (token == JsonToken.START_OBJECT) {
                    if (max > -1 && i > max) {
                        continue;
                    }
                    i++;
                    Map<String, Object> props = new LinkedHashMap<>();
                    String polygon = null;
                    String metroName = null;
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        var name = parser.currentName();
                        Object value = parser.nextTextValue();
                        if (value == null) {
                            value = parser.getIntValue();
                        }
                        //out.println("name= " + name + ", value = " + value);
                        if (name.equals("outline_wkt")) {
                            polygon = parser.getText().substring(13);
                        } else {
                            if (name.equals("full_name")) {
                                //out.println((i-1) + " - fullname: " + value);
                                metroName = (String) value;
                            }
                            props.put(name, value);
                        }

                    }
                    //out.println("props = " + props);
                    if (polygon == null) {
                        out.println("Polygon coordinates are missing!");
                        continue;
                    }
                    polygon = polygon.replaceAll("\\((?=\\d|\\-)", "([");
                    polygon = polygon.replaceAll("(?<=\\d)\\)", "])");
                    polygon = polygon.replaceAll("\\(", "[");
                    polygon = polygon.replaceAll("\\)", "]");
                    polygon = polygon.replaceAll("(?<=\\d),(?=\\d|\\-)", "],[");
                    polygon = polygon.replaceAll("(?<=\\d)\\s+(?=\\d|\\-)", ",");

                    //var arr =  mapper.createArrayNode().rawValueNode(new RawValue(polygon));
                    var arr = mapper.readTree(polygon);
                    out.print(arr.toPrettyString());
                    //we use auto correcting parser to fix any broken json (open / close brackets)
                    writer.writeStartObject();
                    writer.writeStringField("type", "Feature");
                    writer.writeObjectField("properties", props);
                    writer.writeFieldName("geometry");
                    writer.writeStartObject();
                    writer.writeStringField("type", "MultiPolygon");
                    writer.writeFieldName("coordinates");
                    writer.writeObject(arr);
                    writer.writeEndObject();
                    writer.writeEndObject();
                } else if (token == JsonToken.END_ARRAY) {
                    writer.writeEndArray();
                } else if (token != null) {
                   // out.println("not handled token = " + token);
                }

            }
            writer.writeEndObject();
            out.println("processed: " + i + " entries in: " + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
