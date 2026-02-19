/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.inject.Inject;

/**
 * Gradle task that generates REST handler Java source files from the API specification.
 * Inputs: vendored schema.json, the server module's compile classpath (for reflection), and an output directory.
 * The actual generation logic will be implemented in later tasks; this task establishes the pipeline.
 */
public abstract class RestHandlerGeneratorTask extends DefaultTask {

    private final RegularFileProperty schemaFile;
    private final ConfigurableFileCollection serverCompileClasspath;
    private final DirectoryProperty outputDir;

    @Inject
    public RestHandlerGeneratorTask(ObjectFactory objectFactory) {
        this.schemaFile = objectFactory.fileProperty();
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
        Files.createDirectories(outputPath);

        Schema schema = parsed.schema();
        List<Endpoint> endpoints = schema.endpoints() != null ? schema.endpoints() : List.of();
        Iterable<java.io.File> classpath = getServerCompileClasspath().getFiles();

        int generated = 0;
        for (Endpoint endpoint : endpoints) {
            String transportAction = endpoint.serverTransportAction();
            if (transportAction == null || transportAction.isBlank()) {
                continue;
            }
            try {
                ResolvedTransportAction resolvedAction = TransportActionResolver.resolve(transportAction, classpath);
                RestListenerType listenerType = ListenerResolver.resolve(resolvedAction.responseClass());
                org.elasticsearch.gradle.resthandler.model.TypeDefinition requestType = parsed.getRequestType(endpoint);
                com.squareup.javapoet.JavaFile javaFile = HandlerCodeEmitter.emit(
                    endpoint,
                    requestType,
                    resolvedAction,
                    listenerType
                );
                javaFile.writeTo(outputPath);
                generated++;
                getLogger().debug("Generated handler for endpoint {}", endpoint.name());
            } catch (Exception e) {
                getLogger().warn("Skipping endpoint {}: {}", endpoint.name(), e.getMessage());
            }
        }
        getLogger().info(
            "Parsed schema: {} types, {} endpoints; generated {} handler(s)",
            schema.types().size(),
            endpoints.size(),
            generated
        );
    }
}
