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
import org.gradle.api.tasks.bundling.Jar;

/**
 * Gradle plugin that registers the REST handler code generator task and a dedicated
 * {@code restHandlers} source set. Main compiles first; the generator reads main's output
 * and classpath; generated sources are compiled by {@code compileRestHandlersJava}.
 * Applied to the server module (and later to other modules that register REST handlers).
 */
public class RestHandlerGeneratorPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "generateRestHandlers";
    public static final String REST_HANDLERS_SOURCE_SET_NAME = "restHandlers";

    @Override
    public void apply(Project project) {
        Project rootProject = project.getRootProject();
        TaskProvider<RestHandlerGeneratorTask> generateTask = project.getTasks().register(TASK_NAME, RestHandlerGeneratorTask.class);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
            SourceSetContainer sourceSets = javaExtension.getSourceSets();
            SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            SourceSet restHandlersSourceSet = sourceSets.create(REST_HANDLERS_SOURCE_SET_NAME);
            restHandlersSourceSet.setCompileClasspath(
                project.getObjects().fileCollection().from(mainSourceSet.getOutput(), mainSourceSet.getCompileClasspath())
            );
            restHandlersSourceSet.getJava().srcDir(project.getLayout().getBuildDirectory().dir("generated/sources/rest-handlers"));

            generateTask.configure(task -> {
                task.getSchemaFile()
                    .set(rootProject.getLayout().getProjectDirectory().file("rest-api-spec/src/main/resources/schema/schema.json"));
                task.getServerClassesDirs().setFrom(mainSourceSet.getOutput().getClassesDirs());
                task.getServerCompileClasspath().setFrom(mainSourceSet.getCompileClasspath());
                task.getOutputDir().set(project.getLayout().getBuildDirectory().dir("generated/sources/rest-handlers"));
                task.dependsOn(project.getTasks().named(JavaPlugin.COMPILE_JAVA_TASK_NAME));
            });

            project.getTasks().named(restHandlersSourceSet.getCompileJavaTaskName()).configure(compileRestHandlers -> {
                compileRestHandlers.dependsOn(generateTask);
            });

            project.getTasks()
                .named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                .configure(jar -> { jar.from(restHandlersSourceSet.getOutput()); });

            SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
            testSourceSet.setCompileClasspath(testSourceSet.getCompileClasspath().plus(restHandlersSourceSet.getOutput()));
            testSourceSet.setRuntimeClasspath(testSourceSet.getRuntimeClasspath().plus(restHandlersSourceSet.getOutput()));
        });
    }
}
