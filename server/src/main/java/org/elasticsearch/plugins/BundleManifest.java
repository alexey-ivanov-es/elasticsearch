/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugins;

import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.json.JsonXContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.elasticsearch.xcontent.XContentParserConfiguration.EMPTY;

public record BundleManifest(List<String> componentClasses, Map<String, List<RegistryEntryInfo>> registries,
                             Map<String, List<NamedComponentInfo>> namedComponents, Map<String, List<String>> extensionsFields) {

    private static final String FILENAME = "bundle-manifest.json";
    public static final BundleManifest EMPTY = new BundleManifest(List.of(), Map.of(), Map.of(), Map.of());

    public record RegistryEntryInfo(String implementationClass, String categoryClass, String name, String factoryMethod) {}

    public record NamedComponentInfo(String implementationClass, String name) {}

    public static BundleManifest load(Path dir) {
        Path manifestPath = dir.resolve(FILENAME);
        if (Files.exists(manifestPath)) {
            try (var stream = Files.newInputStream(manifestPath)) {
                return load(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return EMPTY;
    }

    public static BundleManifest load(InputStream stream) {
        try (var parser = JsonXContent.jsonXContent.createParser(XContentParserConfiguration.EMPTY, stream)) {

            Map<String, Object> manifestMap = parser.map();
            @SuppressWarnings("unchecked")
            List<String> components = (List<String>) manifestMap.get("components");
            @SuppressWarnings("unchecked")
            Map<String, List<String>> extensionsFields = (Map<String, List<String>>) manifestMap.get("extensions_fields");

            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, String>>> untypedRegistries = (Map<String, List<Map<String, String>>>) manifestMap.get(
                "registries"
            );
            var registries = untypedRegistries.entrySet().stream().map(e ->
                entry(e.getKey(),
                    e.getValue().stream().map(v -> {
                        String implementationClass = v.get("impl");
                        String categoryClass = v.get("category");
                        String name = v.get("name");
                        String factoryMethod = v.get("factoryMethod");
                        return new RegistryEntryInfo(implementationClass, categoryClass, name, factoryMethod);
                    }).toList())
            ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, String>>> untypedNamedComponents = (Map<String, List<Map<String, String>>>) manifestMap.get(
                "namedComponents"
            );
            if (untypedNamedComponents.isEmpty() == false) {
                System.out.println("Untyped named components: " + untypedNamedComponents);
            }
            var namedComponents = untypedNamedComponents.entrySet().stream().map(e -> entry(e.getKey(), e.getValue().stream().map(v -> {
                String implementationClass = v.get("impl");
                String name = v.get("name");
                return new NamedComponentInfo(implementationClass, name);
            }).toList())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            if (namedComponents.isEmpty() == false) {
                System.out.println("Named components: " + namedComponents);
            }

            return new BundleManifest(components, registries, namedComponents, extensionsFields);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
