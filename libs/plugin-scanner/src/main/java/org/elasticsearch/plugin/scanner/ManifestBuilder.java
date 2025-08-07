/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.plugin.scanner;

import org.elasticsearch.plugin.Component;
import org.elasticsearch.plugin.Extensible;
import org.elasticsearch.plugin.Extension;
import org.elasticsearch.plugin.MultipleRegistryEntries;
import org.elasticsearch.plugin.NamedComponent;
import org.elasticsearch.plugin.RegistryCtor;
import org.elasticsearch.plugin.RegistryEntry;
import org.elasticsearch.plugin.RegistryType;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class ManifestBuilder {

    // main method to be used by gradle build plugin
    public static void main(String[] args) throws IOException {
        List<ClassReader> classReaders = ClassReaders.ofClassPath();

        List<String> componentsClasses = findComponents(classReaders);
        Map<String, List<EntryInfo>> registries = findRegistries(classReaders);
        Map<String, List<NamedComponentInfo>> namedComponents = findNamedComponents(classReaders);
        Map<String, Set<String>> extensionsFields = findExtensionsFields(ClassReaders.ofPaths(Stream.of(Path.of(args[1]))));

        Path outputFile = Path.of(args[0]);
        ManifestBuilder.writeToFile(componentsClasses, extensionsFields, registries, namedComponents, outputFile);
    }

    public static void writeToFile(List<String> componentsClasses, Map<String, Set<String>> extensionsFields,
                                   Map<String, List<EntryInfo>> registries,
                                   Map<String, List<NamedComponentInfo>> namedComponents, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            try (XContentBuilder builder = XContentFactory.jsonBuilder(outputStream)) {
                builder.prettyPrint();

                builder.startObject();

                builder.array("components", componentsClasses.toArray(new String[0]));

                builder.startObject("extensions_fields");
                for (Map.Entry<String, Set<String>> entry : extensionsFields.entrySet()) {
                    builder.startArray(entry.getKey());
                    for (var value : entry.getValue()) {
                        builder.value(value);
                    }
                    builder.endArray();
                }
                builder.endObject();

                builder.startObject("registries");
                for (var entry : registries.entrySet()) {
                    builder.startArray(entry.getKey());
                    for (var value : entry.getValue()) {
                        builder.startObject();
                        builder.field("impl", value.implClazz);
                        builder.field("name", value.name);
                        builder.field("category", value.categoryClazz);
                        builder.field("factoryMethod", value.factoryMethodName);
                        builder.endObject();
                    }
                    builder.endArray();
                }
                builder.endObject();

                builder.startObject("namedComponents");
                for (var entry : namedComponents.entrySet()) {
                    builder.startArray(entry.getKey());
                    for (var value : entry.getValue()) {
                        builder.startObject();
                        builder.field("name", value.name);
                        builder.field("impl", value.implClazz);
                        builder.endObject();
                    }
                    builder.endArray();
                }
                builder.endObject();

                builder.endObject();
            }
        }

    }

    public static List<String> findComponents(List<ClassReader> classReaders) {

        ClassScanner componentScanner = new ClassScanner(Type.getDescriptor(Component.class), (path, reader, map) -> {
            String classname = pathToClassName(path);
            map.put(classname, classname);
            return null;
        });
        componentScanner.visit(classReaders);
        // quick hack to reuse ClassScanner
        return componentScanner.getFoundClasses().keySet().stream().sorted().toList();
    }

    public record NamedComponentInfo(String name, String implClazz) {}

    public static Map<String, List<NamedComponentInfo>> findNamedComponents(List<ClassReader> classReaders) {
        ClassScanner extensibleClassScanner = new ClassScanner(Type.getDescriptor(Extensible.class), (classname, reader, map) -> {
            map.put(classname, classname);
            return null;
        });
        extensibleClassScanner.visit(classReaders);

        Map<String, List<NamedComponentInfo>> components = new HashMap<>();
        ClassScanner namedComponentsScanner = new ClassScanner(
            Type.getDescriptor(NamedComponent.class),
            (classname, reader, map) -> new AnnotationVisitor(Opcodes.ASM9) {
                @Override
                public void visit(String key, Object value) {
                    assert key.equals("value");
                    assert value instanceof String;
                    String name = (String) value;

                    String extensibleType = extensibleClassScanner.getFoundClasses().get(classname);
                    if (extensibleType == null) {
                        throw new RuntimeException("Class [" + classname + "] does not extend an Extensible class");
                    }

                    components.computeIfAbsent(pathToClassName(extensibleType), k -> new ArrayList<>()).add(new NamedComponentInfo(name, pathToClassName(classname)));
                }
            }
        );
        namedComponentsScanner.visit(classReaders);

        return components;
    }

    public record EntryInfo(String implClazz, String name, String categoryClazz, String factoryMethodName) {}

    static final String registryCtorDescriptor = Type.getDescriptor(RegistryCtor.class);

    public static Map<String, List<EntryInfo>> findRegistries(List<ClassReader> classReaders) {
        ClassScanner registryTypeScanner = new ClassScanner(Type.getDescriptor(RegistryType.class), (path, reader, map) -> {
            map.put(path, pathToClassName(path));
            return null;
        });
        registryTypeScanner.visit(classReaders);

        Map<String, List<EntryInfo>> registries = new HashMap<>();
        BiFunction<String, ClassReader, AnnotationVisitor> entryVisitorMaker = (classname, reader) -> new AnnotationVisitor(Opcodes.ASM9) {
            String name;
            Type categoryClazz;
            String factoryMethodName;
            Type registryType;

            @Override
            public void visit(String key, Object value) {
                System.out.println("processing annotation: " + key + ", " + value);
                if (key.equals("name")) {
                    assert value instanceof String;
                    name = (String) value;
                } else if (key.equals("category")) {
                    assert value instanceof Type;
                    categoryClazz = (Type) value;
                } else if (key.equals("type")) {
                    assert value instanceof Type;
                    registryType = (Type) value;
                } else {
                    throw new AssertionError("unexpected RegistryEntry key");
                }
            }

            @Override
            public void visitEnd() {
                var methodVisitor = new ClassVisitor(Opcodes.ASM9) {
                    public MethodVisitor visitMethod(int access, String methodname, String desc, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                if (descriptor.equals(registryCtorDescriptor)) {
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        @Override
                                        public void visit(String name, Object value) {
                                            if (name.equals("value")) {
                                                assert value instanceof Type;
                                                if (registryType.equals(value)) {
                                                    factoryMethodName = methodname;
                                                }
                                            } else {
                                                throw new AssertionError("unexpected annotation: " + name);
                                            }
                                        }
                                    };
                                }
                                return null;
                            }
                        };
                    }
                };
                reader.accept(methodVisitor, 0);

                String superRegistryType = registryTypeScanner.getFoundClasses().get(classname);
                if (superRegistryType == null) {
                    throw new RuntimeException("Class [" + classname + "] does not extend registry type");
                }
                registries.computeIfAbsent(registryType.getClassName(), k -> new ArrayList<>())
                    .add(new EntryInfo(pathToClassName(classname), name, categoryClazz.getClassName(), factoryMethodName));
            }
        };
        ClassScanner registryEntryScanner = new ClassScanner(
            Type.getDescriptor(RegistryEntry.class),
            (classname, reader, map) -> {
                System.out.println("found direct RegistryEntry");
                return entryVisitorMaker.apply(classname, reader);
            }
        );
        registryEntryScanner.visit(classReaders);

        ClassScanner multiRegistryEntryScanner = new ClassScanner(
            Type.getDescriptor(MultipleRegistryEntries.class),
            (classname, reader, map) -> {
                System.out.println("found direct MultiRegistryEntry");
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(final String name) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                                System.out.println("inner annotation: " + descriptor);
                                return entryVisitorMaker.apply(classname, reader);
                            }
                        };
                    }
                };
            }
        );
        multiRegistryEntryScanner.visit(classReaders);

        return registries;
    }

    private static Map<String, Set<String>> findExtensionsFields(List<ClassReader> classReaders) {
        Set<String> extensibleClasses = new HashSet<>();
        Map<String, Set<String>> extensionFields = new HashMap<>();

        // TODO: merge with component scanner?
        for (ClassReader classReader : classReaders) {
            classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                private String currentClassName;

                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    currentClassName = pathToClassName(name);
                }

                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (descriptor.equals(Type.getDescriptor(Extensible.class))) {
                        extensibleClasses.add(currentClassName);
                    }
                    return super.visitAnnotation(descriptor, visible);
                }

                @Override
                public FieldVisitor visitField(int access, String fieldName, String descriptor, String signature, Object value) {
                    if (Modifier.isStatic(access) == false || Modifier.isFinal(access) == false) {
                        return super.visitField(access, fieldName, descriptor, signature, value);
                    }
                    return new FieldVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            if (annotationDescriptor.equals(Type.getDescriptor(Extension.class))) {
                                Type type = Type.getType(descriptor);
                                extensionFields.compute(type.getClassName(), (k, set) -> {
                                    if (set == null) {
                                        set = new HashSet<>();
                                    }
                                    set.add(currentClassName + "#" + fieldName);
                                    return set;
                                });
                            }
                            return super.visitAnnotation(annotationDescriptor, visible);
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
        }

        if (extensibleClasses.containsAll(extensionFields.keySet()) == false) {
            System.out.println(extensibleClasses);
            System.out.println(extensionFields);
            extensionFields.keySet().removeAll(extensibleClasses);
            // TODO: we don't scan dependencies now, so we can't check that class actually extends the right thing
//            throw new RuntimeException("Some extension fields are not defined as extensible classes: " + extensionFields.values());
        }
        return extensionFields;
    }

    private static String pathToClassName(String classWithSlashes) {
        return classWithSlashes.replace('/', '.');
    }

}
