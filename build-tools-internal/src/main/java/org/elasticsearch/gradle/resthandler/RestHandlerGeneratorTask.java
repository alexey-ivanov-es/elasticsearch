/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;

import org.elasticsearch.gradle.resthandler.model.Endpoint;
import org.elasticsearch.gradle.resthandler.model.Schema;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

/**
 * Gradle task that generates REST handler Java source files from the API specification.
 * Inputs: vendored schema.json, the server main source set's classes directories and compile
 * classpath (for reflection), and an output directory. Creates a URLClassLoader from these
 * inputs for the duration of the task and closes it when done.
 */
public abstract class RestHandlerGeneratorTask extends DefaultTask {

    private final RegularFileProperty schemaFile;
    private final ConfigurableFileCollection serverClassesDirs;
    private final ConfigurableFileCollection serverCompileClasspath;
    private final DirectoryProperty outputDir;

    @Inject
    public RestHandlerGeneratorTask(ObjectFactory objectFactory) {
        this.schemaFile = objectFactory.fileProperty();
        this.serverClassesDirs = objectFactory.fileCollection();
        this.serverCompileClasspath = objectFactory.fileCollection();
        this.outputDir = objectFactory.directoryProperty();
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public RegularFileProperty getSchemaFile() {
        return schemaFile;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getServerClassesDirs() {
        return serverClassesDirs;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public ConfigurableFileCollection getServerCompileClasspath() {
        return serverCompileClasspath;
    }

    @OutputDirectory
    public DirectoryProperty getOutputDir() {
        return outputDir;
    }

    @TaskAction
    public void generate() throws IOException {
        Path schemaPath = getSchemaFile().get().getAsFile().toPath();
        ParsedSchema parsed = SchemaParser.parse(schemaPath);
        Path outputPath = getOutputDir().get().getAsFile().toPath();
        if (Files.exists(outputPath)) {
            try (var stream = Files.walk(outputPath)) {
                stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
        Files.createDirectories(outputPath);

        Schema schema = parsed.schema();
        List<Endpoint> endpoints = schema.endpoints() != null ? schema.endpoints() : List.of();

        Iterable<java.io.File> classesFiles = getServerClassesDirs().getFiles();
        Iterable<java.io.File> classpathFiles = getServerCompileClasspath().getFiles();
        URL[] urls = StreamSupport.stream(classesFiles.spliterator(), false).filter(java.io.File::exists).map(f -> {
            try {
                return f.toURI().toURL();
            } catch (java.net.MalformedURLException e) {
                throw new IllegalArgumentException("Invalid path: " + f, e);
            }
        }).toArray(URL[]::new);
        URL[] classpathUrls = StreamSupport.stream(classpathFiles.spliterator(), false).filter(java.io.File::exists).map(f -> {
            try {
                return f.toURI().toURL();
            } catch (java.net.MalformedURLException e) {
                throw new IllegalArgumentException("Invalid path: " + f, e);
            }
        }).toArray(URL[]::new);
        URL[] allUrls = new URL[urls.length + classpathUrls.length];
        System.arraycopy(urls, 0, allUrls, 0, urls.length);
        System.arraycopy(classpathUrls, 0, allUrls, urls.length, classpathUrls.length);

        List<ClassName> generatedHandlerClasses = new ArrayList<>();
        try (URLClassLoader loader = new URLClassLoader(allUrls, ClassLoader.getPlatformClassLoader())) {
            for (Endpoint endpoint : endpoints) {
                String transportAction = endpoint.serverTransportAction();
                if (transportAction == null || transportAction.isBlank()) {
                    continue;
                }
                try {
                    ResolvedTransportAction resolvedAction = TransportActionResolver.resolve(transportAction, loader);
                    RestListenerType listenerType = ListenerResolver.resolve(resolvedAction.responseClass());
                    org.elasticsearch.gradle.resthandler.model.TypeDefinition requestType = parsed.getRequestType(endpoint);
                    JavaFile javaFile = HandlerCodeEmitter.emit(endpoint, requestType, resolvedAction, listenerType);
                    javaFile.writeTo(outputPath);
                    String packageName = HandlerCodeEmitter.packageForTransportAction(resolvedAction.transportActionClass().getName());
                    String handlerClassName = HandlerCodeEmitter.handlerClassNameForEndpoint(endpoint.name());
                    generatedHandlerClasses.add(ClassName.get(packageName, handlerClassName));
                    getLogger().debug("Generated handler for endpoint {}", endpoint.name());
                } catch (Exception e) {
                    getLogger().warn("Skipping endpoint {}: {}", endpoint.name(), e.getMessage());
                }
            }
        }

        if (!generatedHandlerClasses.isEmpty()) {
            JavaFile registryFile = HandlerCodeEmitter.emitRegistry(generatedHandlerClasses);
            registryFile.writeTo(outputPath);
        }

        getLogger().info(
            "Parsed schema: {} types, {} endpoints; generated {} handler(s)",
            schema.types().size(),
            endpoints.size(),
            generatedHandlerClasses.size()
        );
    }
}
