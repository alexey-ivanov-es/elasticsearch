/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.gradle.resthandler;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin that registers the REST handler code generator task and wires its output
 * into the project's main Java source set. Applied to the server module (and later to other
 * modules that register REST handlers).
 */
public class RestHandlerGeneratorPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "generateRestHandlers";

    @Override
    public void apply(Project project) {
        Project rootProject = project.getRootProject();
        TaskProvider<RestHandlerGeneratorTask> generateTask = project.getTasks()
            .register(TASK_NAME, RestHandlerGeneratorTask.class);

        generateTask.configure(task -> {
            task.getSchemaFile().set(
                rootProject.getLayout().getProjectDirectory().file("rest-api-spec/src/main/resources/schema/schema.json")
            );
            task.getServerCompileClasspath().setFrom(
                project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME).getCompileClasspath()
            );
            task.getOutputDir().set(project.getLayout().getBuildDirectory().dir("generated/sources/rest-handlers"));
        });

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            SourceSetContainer sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            mainSourceSet.getJava().srcDir(generateTask);
        });
    }
}
