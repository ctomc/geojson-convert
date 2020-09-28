package io.global.geolocation.tools;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.System.out;

public class Converter {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String... args) throws IOException {
        out.println("Args: " + Arrays.asList(args));
        if (args.length != 2) {
            System.err.println("Requires input and output file");
            System.exit(1);
        }
        var inFile = args[0];
        var outFile = args[1];

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
                    Map<String, Object> props = new LinkedHashMap<>();
                    String poligon = null;
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        var name = parser.currentName();
                        Object value = parser.nextTextValue();
                        if (value == null) {
                            value = parser.getIntValue();
                        }
                        //out.println("name= " + name + ", value = " + value);
                        if (name.equals("outline_wkt")) {
                            poligon = parser.getText().substring(13);
                        } else {
                            props.put(name, value);
                        }

                    }
                    //out.println("props = " + props);
                    if (poligon == null) {
                        out.println("Poligon coordinates are missing!");
                        continue;
                    }

                    poligon = poligon
                            .replaceAll("\\(", "[")
                            .replaceAll("\\)", "]")
                            .replaceAll(",", "],[")
                            .replaceAll(" ", ",");
                    //out.println("poligon = " + poligon);

                    //var arr =  mapper.createArrayNode().rawValueNode(new RawValue(poligon));
                    var arr = mapper.readTree(poligon); //we use auto correcting parser to fix any broken json (open / close brackets)

                    writer.writeStartObject();
                    writer.writeStringField("type", "Feature");
                    writer.writeObjectField("properties", props);
                    writer.writeFieldName("geometry");
                    writer.writeStartObject();
                    writer.writeStringField("type", "Polygon");
                    writer.writeFieldName("coordinates");
                    writer.writeObject(arr);
                    writer.writeEndObject();
                    writer.writeEndObject();
                    i++;
                } else if (token == JsonToken.END_ARRAY) {
                    writer.writeEndArray();
                } else if (token != null) {
                    out.println("not handled token = " + token);
                }

            }
            writer.writeEndObject();
            out.println("processed: " + i + " entries in: " + (System.currentTimeMillis() - start) + "ms");
        }
    }
}
